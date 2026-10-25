/*
 * Copyright 2022 OmniFish
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
package ee.omnifish.omnibeans;

import jakarta.ejb.EJBHome;
import jakarta.ejb.EJBLocalHome;
import jakarta.ejb.EJBLocalObject;
import jakarta.ejb.EJBObject;
import jakarta.ejb.SessionContext;
import jakarta.ejb.TimerService;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import jakarta.security.jacc.PolicyContext;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import java.security.Principal;
import java.util.Map;

import javax.security.auth.Subject;

import org.omnifaces.services.asynchronous.AsynchronousInvocation;

import ee.omnifish.omnibeans.security.Callers;
import ee.omnifish.omnibeans.security.Callers.Caller;
import ee.omnifish.omnibeans.security.JakartaAuthorization;
import ee.omnifish.omnibeans.security.SecurityDescriptor;
import ee.omnifish.omnibeans.security.SecurityDescriptorRegistry;
import ee.omnifish.omnibeans.transactions.JakartaTransactions;

/**
 * The session context of a session descriptor, the one it's injected into. Its caller is the one of the session bean call
 * in progress: the identity on the thread, except in a bean that runs as a role, where it's the one that called the
 * descriptor. Whether the caller is in a role is up to the policy: the bean's {@code EJBRoleRefPermission} for it, in the
 * session beans' policy context.
 */
@Dependent
public class SessionContextImpl implements SessionContext {

    @Inject
    private InjectionPoint injectionPoint;

    @Inject
    private SecurityDescriptorRegistry registry;

    @Inject
    private Instance<UserTransaction> userTransaction;

    @Inject
    private Instance<TransactionSynchronizationRegistry> transactionSynchronizationRegistry;

    /**
     * Creates the session context. Used by CDI.
     */
    public SessionContextImpl() {
    }

    @Override
    public Principal getCallerPrincipal() throws IllegalStateException {
        String contextId = getContextId();
        return JakartaAuthorization.getCallerPrincipal(contextId, getCaller(contextId));
    }

    @Override
    public boolean isCallerInRole(String roleName) throws IllegalStateException {
        Class<?> owner = getOwner();
        SecurityDescriptor descriptor = owner != null ? registry.get(owner) : null;
        if (descriptor == null) {
            return false;
        }

        String contextId = JakartaAuthorization.getContextId();
        return JakartaAuthorization.isCallerInRole(contextId, descriptor.getName(), getCaller(contextId), roleName);
    }

    // The session beans' policy context, else the one on the thread
    private static String getContextId() {
        String contextId = JakartaAuthorization.getContextId();
        return contextId != null ? contextId : PolicyContext.getContextID();
    }

    // In a bean that runs as a role, the caller it saved; otherwise the identity on the thread
    private Subject getCaller(String contextId) {
        Caller caller = Callers.current();
        Class<?> owner = getOwner();
        if (caller != null && owner != null && caller.beanClass() == owner) {
            return caller.subject();
        }

        return JakartaAuthorization.getCallerSubject(contextId);
    }

    // The bean this was injected into, or null if it was looked up
    private Class<?> getOwner() {
        Bean<?> bean = injectionPoint != null ? injectionPoint.getBean() : null;
        return bean != null ? bean.getBeanClass() : null;
    }

    @Override
    public UserTransaction getUserTransaction() throws IllegalStateException {
        return JakartaTransactions.getUserTransaction(userTransaction);
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException {
        JakartaTransactions.getTransactionSynchronizationRegistry(transactionSynchronizationRegistry).setRollbackOnly();
    }

    @Override
    public boolean getRollbackOnly() throws IllegalStateException {
        return JakartaTransactions.getTransactionSynchronizationRegistry(transactionSynchronizationRegistry).getRollbackOnly();
    }

    @Override
    public TimerService getTimerService() throws IllegalStateException {
        return null;
    }

    @Override
    public Object lookup(String name) throws IllegalArgumentException {
        return null;
    }

    @Override
    public Map<String, Object> getContextData() {
        return null;
    }

    @Override
    public <T> T getBusinessObject(Class<T> businessInterface) throws IllegalStateException {
        return null;
    }

    @Override
    public Class getInvokedBusinessInterface() throws IllegalStateException {
        return null;
    }

    @Override
    public boolean wasCancelCalled() throws IllegalStateException {
        return AsynchronousInvocation.wasCancelCalled();
    }

    @Override
    public EJBHome getEJBHome() throws IllegalStateException {
        return null;
    }

    @Override
    public EJBLocalHome getEJBLocalHome() throws IllegalStateException {
        return null;
    }

    @Override
    public EJBLocalObject getEJBLocalObject() throws IllegalStateException {
        return null;
    }

    @Override
    public EJBObject getEJBObject() throws IllegalStateException {
        return null;
    }

}
