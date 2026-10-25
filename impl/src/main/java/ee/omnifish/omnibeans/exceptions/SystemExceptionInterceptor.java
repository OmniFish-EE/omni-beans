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
package ee.omnifish.omnibeans.exceptions;

import jakarta.annotation.Priority;
import jakarta.ejb.ApplicationException;
import jakarta.ejb.ConcurrentAccessException;
import jakarta.ejb.ConcurrentAccessTimeoutException;
import jakarta.ejb.EJBException;
import jakarta.ejb.EJBTransactionRequiredException;
import jakarta.ejb.EJBTransactionRolledbackException;
import jakarta.ejb.IllegalLoopbackException;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.TransactionRequiredException;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionalException;

import java.io.Serializable;
import java.util.logging.Logger;

import org.omnifaces.services.lock.Lock;
import org.omnifaces.services.lock.LockTimeoutException;
import org.omnifaces.services.pooled.Pooled;

import ee.omnifish.omnibeans.transactions.JakartaTransactions;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;
import static jakarta.transaction.Transactional.TxType.MANDATORY;
import static jakarta.transaction.Transactional.TxType.NEVER;
import static jakarta.transaction.Transactional.TxType.REQUIRED;
import static jakarta.transaction.Transactional.TxType.SUPPORTS;
import static java.util.logging.Level.WARNING;
import static org.omnifaces.services.lock.Lock.TimeoutType.NOT_PERMITTED;

/**
 * Handles the exceptions of a session bean's business methods as an Enterprise Beans container does.
 *
 * <p>
 * An application exception (a checked exception, or an unchecked one annotated {@link ApplicationException}) is
 * rethrown as is. A system exception (any other exception or error) is logged, a stateless bean's instance is discarded,
 * and it's thrown to the caller as an {@link EJBTransactionRolledbackException} when the method ran in the caller's
 * transaction, else as an {@link EJBException}.
 *
 * <p>
 * A call that is refused without invoking the method is thrown to the caller as the Enterprise Beans container does,
 * without logging it or discarding the instance: an {@link EJBTransactionRequiredException} for a {@code MANDATORY}
 * method called without a transaction, an {@link EJBException} for a {@code NEVER} method called with one, and a
 * {@link ConcurrentAccessTimeoutException} for a singleton's lock that isn't acquired in time (a
 * {@link ConcurrentAccessException} when waiting isn't permitted), and an {@link IllegalLoopbackException} for a call
 * that holds a singleton's read lock and reaches a method of it with a write lock again.
 *
 * <p>
 * Its priority puts it after the pooled scope interceptor, so a stateless bean's instance is still taken from the pool,
 * and before the lock and transactional interceptors, so the transaction has ended. The attribute
 * {@code ApplicationException.rollback} isn't supported: the transaction ends as {@link Transactional} specifies.
 *
 * @serial exclude
 */
@Interceptor
@SystemExceptions
@Priority(PLATFORM_BEFORE + 60)
public class SystemExceptionInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(SystemExceptionInterceptor.class.getName());

    @Inject
    private BeanManager beanManager;

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    @Inject
    private Instance<TransactionSynchronizationRegistry> transactionSynchronizationRegistry;

    /**
     * Invokes the method, and handles a system exception it throws.
     *
     * @param ctx the invocation context
     * @return what the method returned
     * @throws Exception an application exception the method threw, or an {@link EJBException} for a system exception
     */
    @AroundInvoke
    public Object handleSystemExceptions(InvocationContext ctx) throws Exception {
        boolean inCallerTransaction = isInCallerTransaction(ctx);

        try {
            return ctx.proceed();
        } catch (Exception exception) {
            if (isApplicationException(exception)) {
                throw exception;
            }

            EJBException refusal = getRefusal(ctx, exception);
            if (refusal != null) {
                throw refusal;
            }

            throw handleSystemException(ctx, exception, inCallerTransaction);
        } catch (Error error) {
            throw handleSystemException(ctx, error, inCallerTransaction);
        }
    }

    /**
     * Returns whether the exception is an application exception: a checked exception, or an unchecked exception whose
     * class is annotated {@link ApplicationException}, or whose superclass is, with {@code inherited} true.
     *
     * @param exception the exception
     * @return whether the exception is an application exception
     */
    static boolean isApplicationException(Exception exception) {
        if (!(exception instanceof RuntimeException)) {
            return true;
        }

        for (Class<?> type = exception.getClass(); type != RuntimeException.class; type = type.getSuperclass()) {
            ApplicationException applicationException = type.getDeclaredAnnotation(ApplicationException.class);
            if (applicationException != null) {
                return type == exception.getClass() || applicationException.inherited();
            }
        }

        return false;
    }

    /**
     * Returns the exception for a call that was refused without invoking the method, or null if it's not such an
     * exception.
     */
    private static EJBException getRefusal(InvocationContext ctx, Exception exception) {
        if (exception instanceof TransactionalException) {
            return getTransactionRefusal(ctx, exception);
        }

        if (exception instanceof org.omnifaces.services.lock.IllegalLoopbackException) {
            EJBException refusal = new IllegalLoopbackException("Method " + ctx.getMethod().getName() + ": " + exception.getMessage());
            refusal.initCause(exception);
            return refusal;
        }

        if (exception instanceof LockTimeoutException) {
            Lock lock = ctx.getInterceptorBinding(Lock.class);
            String message = "Method " + ctx.getMethod().getName() + ": " + exception.getMessage();
            EJBException refusal = lock != null && lock.timeoutType() == NOT_PERMITTED ? new ConcurrentAccessException(message) : new ConcurrentAccessTimeoutException(message);
            refusal.initCause(exception);
            return refusal;
        }

        return null;
    }

    /**
     * Returns the exception for a call that the transactional interceptor refused because of the method's transaction
     * type, or null if it's not such an exception.
     */
    private static EJBException getTransactionRefusal(InvocationContext ctx, Exception exception) {
        Transactional transactional = ctx.getInterceptorBinding(Transactional.class);
        if (transactional == null) {
            return null;
        }

        if (transactional.value() == MANDATORY && exception.getCause() instanceof TransactionRequiredException) {
            EJBException refusal = new EJBTransactionRequiredException("Method " + ctx.getMethod().getName() + " requires a transaction");
            refusal.initCause(exception);
            return refusal;
        }

        if (transactional.value() == NEVER && exception.getCause() instanceof InvalidTransactionException) {
            return new EJBException("Method " + ctx.getMethod().getName() + " must not be called with a transaction", exception);
        }

        return null;
    }

    private EJBException handleSystemException(InvocationContext ctx, Throwable throwable, boolean inCallerTransaction) {
        logger.log(WARNING, "System exception in " + ctx.getMethod() + " of session bean " + interceptedBean.getBeanClass().getName(), throwable);

        if (Pooled.class.equals(interceptedBean.getScope())) {
            // Discards the stateless bean's instance, which this thread holds; the pool gets a new one
            ((AlterableContext) beanManager.getContext(Pooled.class)).destroy(interceptedBean);
        }

        if (throwable instanceof EJBException ejbException && (!inCallerTransaction || throwable instanceof EJBTransactionRolledbackException)) {
            return ejbException;
        }

        String message = "System exception in " + ctx.getMethod().getName() + ": " + throwable;
        if (throwable instanceof Exception exception) {
            return inCallerTransaction ? new EJBTransactionRolledbackException(message, exception) : new EJBException(message, exception);
        }

        // An error: EJBException.getCausedByException() only supports exceptions, getCause() returns the error
        EJBException ejbException = inCallerTransaction ? new EJBTransactionRolledbackException(message) : new EJBException(message);
        ejbException.initCause(throwable);

        return ejbException;
    }

    private boolean isInCallerTransaction(InvocationContext ctx) {
        Transactional transactional = ctx.getInterceptorBinding(Transactional.class);
        if (transactional == null || !(transactional.value() == REQUIRED || transactional.value() == MANDATORY || transactional.value() == SUPPORTS)) {
            return false;
        }

        return JakartaTransactions.getTransactionSynchronizationRegistry(transactionSynchronizationRegistry).getTransactionKey() != null;
    }
}
