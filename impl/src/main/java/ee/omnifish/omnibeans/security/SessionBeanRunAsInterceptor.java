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

import jakarta.annotation.Priority;
import jakarta.ejb.EJBException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.io.Serializable;

import ee.omnifish.omnibeans.security.Callers.Caller;
import ee.omnifish.omnibeans.spi.RunAsHandler;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Runs the business methods of a session bean with {@code @RunAs} as its run-as role, through the container's
 * {@link RunAsHandler}. The bean itself still sees its caller ({@link Callers}); the beans it calls see the role.
 * <p>
 * Runs after the authorization of the call ({@link SessionBeanSecurityInterceptor}), and after Quarkus' own security
 * checks, which a session bean can inherit from a superclass, so that both check the caller; and before the
 * transaction.
 *
 * @serial exclude
 */
@Interceptor
@SessionBeanRunAs
@Priority(PLATFORM_BEFORE + 155)
public class SessionBeanRunAsInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    @Inject
    private SecurityDescriptorRegistry registry;

    @Inject
    private Instance<RunAsHandler> runAsHandler;

    /**
     * Makes the call as the bean's run-as role.
     *
     * @param ctx the invocation context
     * @return what the method returned
     * @throws Exception what the method threw
     */
    @AroundInvoke
    public Object runAs(InvocationContext ctx) throws Exception {
        Class<?> beanClass = interceptedBean.getBeanClass();
        SecurityDescriptor descriptor = registry.get(beanClass);
        if (descriptor == null || descriptor.getRunAs() == null) {
            return ctx.proceed();
        }

        RunAsHandler handler = getRunAsHandler(descriptor);
        Caller caller = new Caller(beanClass, JakartaAuthorization.getCallerSubject(JakartaAuthorization.getContextId()));
        return Callers.call(caller, () -> handler.runAs(descriptor.getRunAs(), ctx::proceed));
    }

    private RunAsHandler getRunAsHandler(SecurityDescriptor descriptor) {
        if (!runAsHandler.isResolvable()) {
            throw new EJBException(
                "Session bean " + descriptor.getName() + " runs as " + descriptor.getRunAs() + ", which needs a "
                + RunAsHandler.class.getName() + " bean from the container");
        }

        return runAsHandler.get();
    }
}
