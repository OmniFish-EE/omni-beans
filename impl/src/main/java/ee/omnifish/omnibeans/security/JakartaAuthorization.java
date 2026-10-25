/*
 * Copyright 2026 OmniFish
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package ee.omnifish.omnibeans.security;

import jakarta.ejb.EJBAccessException;
import jakarta.security.jacc.EJBMethodPermission;
import jakarta.security.jacc.EJBRoleRefPermission;
import jakarta.security.jacc.Policy;
import jakarta.security.jacc.PolicyConfiguration;
import jakarta.security.jacc.PolicyConfigurationFactory;
import jakarta.security.jacc.PolicyContext;
import jakarta.security.jacc.PolicyContextException;
import jakarta.security.jacc.PolicyFactory;
import jakarta.security.jacc.PrincipalMapper;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import ee.omnifish.omnibeans.security.SecurityDescriptor.MethodPermission;

import static jakarta.security.jacc.PolicyContext.PRINCIPAL_MAPPER;
import static jakarta.security.jacc.PolicyContext.SUBJECT;
import static java.util.logging.Level.FINE;

/**
 * Authorizes session bean calls through Jakarta Authorization, as an Enterprise Beans container does: the container's
 * policy decides whether it grants the caller the {@link EJBMethodPermission} of the call, and whether the caller is
 * in a role ({@link EJBRoleRefPermission}). The container provides the caller.
 *
 * <p>
 * As the specification requires for enterprise beans in a web module, the session beans' statements are in a policy
 * context of their own: the web module's policy context ID followed by {@code _internal}, as in GlassFish. It's linked
 * to the web module's one, and committed at startup, before the application's listeners run
 * ({@link SessionBeanPolicyContextInitializer}), so they can call secured beans too, and the web module's own commits leave
 * it alone. Authorized calls run in it.
 * <p>
 * Without a policy context, such as on a thread the container didn't set one on, only the methods that the
 * annotations leave unchecked can be called.
 */
final public class JakartaAuthorization {

    private static final Logger logger = Logger.getLogger(JakartaAuthorization.class.getName());

    private static final String CONTEXT_ID_SUFFIX = "_internal";

    // The method interface of the local business interfaces and the no-interface view, all that Enterprise Beans Lite has
    private static final String LOCAL = "Local";

    private static final Principal UNAUTHENTICATED = new NamedPrincipal("ANONYMOUS");

    // Per web module's policy context, the session beans' one
    private static final Map<String, String> CONTEXT_IDS = new ConcurrentHashMap<>();

    // The session beans' policy contexts
    private static final Set<String> SESSION_BEAN_CONTEXT_IDS = ConcurrentHashMap.newKeySet();

    private JakartaAuthorization() {
    }

    /**
     * Puts the session beans' statements into service, in a policy context of their own that's linked to the web
     * module's one.
     *
     * @param webContextId the web module's policy context
     * @param registry the security descriptors of the session beans
     */
    static void deploy(String webContextId, SecurityDescriptorRegistry registry) {
        String contextId = webContextId + CONTEXT_ID_SUFFIX;

        try {
            ExousiaHandlers.register(webContextId, contextId);

            PolicyConfigurationFactory factory = PolicyConfigurationFactory.get();
            PolicyConfiguration policyConfiguration = factory.getPolicyConfiguration(contextId, true);

            addAllPermissionsToPolicy(registry, policyConfiguration);

            PolicyConfiguration webPolicyConfiguration = factory.getPolicyConfiguration(webContextId);
            if (webPolicyConfiguration != null) {
                policyConfiguration.linkConfiguration(webPolicyConfiguration);
            }

            policyConfiguration.commit();
            getPolicy(contextId).refresh();
        } catch (PolicyContextException | RuntimeException e) {
            ExousiaHandlers.remove(contextId);
            throw new IllegalStateException("Could not put the statements of the session beans into service, in policy context " + contextId, e);
        }

        CONTEXT_IDS.put(webContextId, contextId);
        SESSION_BEAN_CONTEXT_IDS.add(contextId);

        logger.log(FINE, () -> "Put the statements of " + registry.size() + " session beans into service, in policy context " + contextId);
    }

    /**
     * Takes the session beans' statements out of service.
     *
     * @param webContextId the web module's policy context
     */
    static void undeploy(String webContextId) {
        String contextId = CONTEXT_IDS.remove(webContextId);
        if (contextId == null) {
            return;
        }

        SESSION_BEAN_CONTEXT_IDS.remove(contextId);
        try {
            PolicyConfiguration policyConfiguration = PolicyConfigurationFactory.get().getPolicyConfiguration(contextId);
            if (policyConfiguration != null) {
                policyConfiguration.delete();
            }

            getPolicy(contextId).refresh();
        } catch (PolicyContextException | RuntimeException e) {
            logger.log(FINE, "Could not delete policy context " + contextId, e);
        } finally {
            ExousiaHandlers.remove(contextId);
        }
    }

    /**
     * Checks that the policy grants the current caller the call of a session bean's business method.
     *
     * @param securityDescriptor the session bean's security descriptor
     * @param method the business method
     * @return the session beans' policy context to run the call in, or null if there's no policy context
     * @throws EJBAccessException if the policy doesn't grant it, or if it can't be checked
     */
    static String authorize(SecurityDescriptor securityDescriptor, Method method) {
        String threadContextId = PolicyContext.getContextID();
        if (threadContextId == null) {
            if (securityDescriptor.isUnchecked(method)) {
                return null;
            }

            throw new EJBAccessException(
                "Method " + method.getName() + " of session bean " + securityDescriptor.getName() + " can't be authorized: there's no "
                + "Jakarta Authorization policy context on this thread");
        }

        String contextId = getContextId(threadContextId);
        if (contextId == null) {
            throw new EJBAccessException(
                "Method " + method.getName() + " of session bean " + securityDescriptor.getName() + " can't be authorized: the "
                + "statements of the session beans of policy context " + threadContextId + " weren't added at startup");
        }

        Subject caller;
        boolean granted;
        try {
            caller = getCallerSubject(contextId);
            granted = inContext(
                        contextId,
                        () -> getPolicy(contextId).implies(new EJBMethodPermission(securityDescriptor.getName(), LOCAL, method), caller));

        } catch (RuntimeException e) {
            EJBAccessException accessException = new EJBAccessException(
                "Method " + method.getName() + " of session bean " + securityDescriptor.getName() + " can't be authorized: " + e);
            accessException.initCause(e);
            throw accessException;
        }

        if (!granted) {
            throw new EJBAccessException(
                "Caller " + getCallerName(contextId, caller) + " may not call method " + method.getName() + " of session bean "
                + securityDescriptor.getName());
        }

        return contextId;
    }

    /**
     * Returns the session beans' policy context for the policy context on the current thread.
     *
     * @return the session beans' policy context, or null if there's none for the thread's one, or no policy context
     */
    public static String getContextId() {
        String threadContextId = PolicyContext.getContextID();
        return threadContextId != null ? getContextId(threadContextId) : null;
    }

    private static String getContextId(String threadContextId) {
        return SESSION_BEAN_CONTEXT_IDS.contains(threadContextId) ? threadContextId : CONTEXT_IDS.get(threadContextId);
    }

    /**
     * Returns the current caller.
     *
     * @param contextId the policy context to ask, or null if none
     * @return the caller, which has no principals if it isn't authenticated
     */
    public static Subject getCallerSubject(String contextId) {
        if (contextId == null) {
            return new Subject();
        }

        Subject subject = inContext(contextId, () -> PolicyContext.get(SUBJECT));
        return subject != null ? subject : new Subject();
    }

    /**
     * Returns the principal of a caller.
     *
     * @param contextId the policy context whose principal mapper to use, or null if none
     * @param caller the caller
     * @return the caller's principal, or one named {@code ANONYMOUS} if the caller isn't authenticated
     */
    public static Principal getCallerPrincipal(String contextId, Subject caller) {
        if (contextId == null) {
            return UNAUTHENTICATED;
        }

        return inContext(contextId, () -> {
            PrincipalMapper principalMapper = PolicyContext.get(PRINCIPAL_MAPPER);
            Principal principal = principalMapper != null ? principalMapper.getCallerPrincipal(caller) : null;
            return principal != null ? principal : UNAUTHENTICATED;
        });
    }

    /**
     * Tells whether the policy puts a caller in a role, for a session bean.
     *
     * @param contextId the session beans' policy context, or null if none
     * @param beanName the session bean's name
     * @param caller the caller
     * @param role the role
     * @return whether the caller is in the role
     */
    public static boolean isCallerInRole(String contextId, String beanName, Subject caller, String role) {
        if (contextId == null) {
            return false;
        }

        return inContext(contextId, () -> getPolicy(contextId).implies(new EJBRoleRefPermission(beanName, role), caller));
    }

    // For a message only, so it doesn't fail
    private static String getCallerName(String contextId, Subject subject) {
        try {
            return getCallerPrincipal(contextId, subject).getName();
        } catch (RuntimeException e) {
            return "(unknown)";
        }
    }

    private static Policy getPolicy(String contextId) {
        Policy policy = PolicyFactory.getPolicyFactory().getPolicy(contextId);
        if (policy == null) {
            throw new IllegalStateException("No Jakarta Authorization policy for policy context " + contextId);
        }

        return policy;
    }

    /**
     * Translates the security descriptors of all session beans into statements of a policy configuration.
     *
     * @param registry the security descriptors
     * @throws PolicyContextException if the policy configuration fails
     * @param policyConfiguration the open policy configuration
     */
    private static void addAllPermissionsToPolicy(SecurityDescriptorRegistry registry, PolicyConfiguration policyConfiguration) throws PolicyContextException {
        for (SecurityDescriptor descriptor : registry.getAll()) {
            addPermissionsToPolicy(descriptor, registry.getRoles(), policyConfiguration);
        }
    }

    /**
     * Translates the security descriptor of a session bean into statements of a policy configuration: the
     * {@link EJBMethodPermission} of each business method as excluded, unchecked or for each of its roles, and to each
     * application role the bean's {@link EJBRoleRefPermission} for it.
     *
     * @param descriptor the security descriptor
     * @param applicationRoles the application's roles
     * @param policyConfiguration the open policy configuration
     * @throws PolicyContextException if the policy configuration fails
     */
    static void addPermissionsToPolicy(SecurityDescriptor descriptor, Set<String> applicationRoles, PolicyConfiguration policyConfiguration) throws PolicyContextException {
        String name = descriptor.getName();

        for (MethodPermission methodPermission : descriptor.getMethodPermissions()) {
            EJBMethodPermission permission =
                new EJBMethodPermission(name, methodPermission.name(), LOCAL, methodPermission.parameterTypes());

            switch (methodPermission.access()) {
                case EXCLUDED -> policyConfiguration.addToExcludedPolicy(permission);
                case UNCHECKED -> policyConfiguration.addToUncheckedPolicy(permission);
                case ROLES -> {
                    for (String role : methodPermission.roles()) {
                        policyConfiguration.addToRole(role, permission);
                    }
                }
            }
        }

        for (String applicationRole : applicationRoles) {
            policyConfiguration.addToRole(applicationRole, new EJBRoleRefPermission(name, applicationRole));
        }
    }

    /**
     * Runs an action with a policy context on the current thread, and restores the thread's one afterwards.
     *
     * @param <T> the type of the action's result
     * @param contextId the policy context
     * @param action the action
     * @return what the action returned
     */
    static <T> T inContext(String contextId, Supplier<T> action) {
        String threadContextId = PolicyContext.getContextID();
        if (contextId.equals(threadContextId)) {
            return action.get();
        }

        PolicyContext.setContextID(contextId);
        try {
            return action.get();
        } finally {
            PolicyContext.setContextID(threadContextId);
        }
    }

    /**
     * Makes a call with a policy context on the current thread, and restores the thread's one afterwards.
     *
     * @param contextId the policy context
     * @param call the call
     * @return what the call returned
     * @throws Exception what the call threw
     */
    static Object callInContext(String contextId, Callable<Object> call) throws Exception {
        String threadContextId = PolicyContext.getContextID();
        if (contextId.equals(threadContextId)) {
            return call.call();
        }

        PolicyContext.setContextID(contextId);
        try {
            return call.call();
        } finally {
            PolicyContext.setContextID(threadContextId);
        }
    }

    private record NamedPrincipal(String name) implements Principal {

        @Override
        public String getName() {
            return name;
        }
    }
}
