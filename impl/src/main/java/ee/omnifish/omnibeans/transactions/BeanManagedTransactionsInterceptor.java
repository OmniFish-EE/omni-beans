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
package ee.omnifish.omnibeans.transactions;

import jakarta.annotation.Priority;
import jakarta.ejb.EJBException;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.UserTransaction;

import java.io.Serializable;
import java.util.logging.Logger;

import ee.omnifish.omnibeans.exceptions.SystemExceptionInterceptor;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static java.util.logging.Level.WARNING;

/**
 * Checks that a session bean with Bean Managed Transactions completes the transactions it starts, as an Enterprise
 * Beans container must.
 *
 * <p>
 * If a business method, or one of its interceptors, returns with a transaction still open, the transaction is rolled back, and the
 * caller gets an {@link EJBException}, after which the {@link SystemExceptionInterceptor} discards a stateless bean's instance.
 *
 * <p>
 * Its priority puts it inside the transactional interceptor of {@code NOT_SUPPORTED}, which OmniBeans maps Bean Managed
 * Transactions to, so the caller's transaction is suspended, and before the interceptors of {@code Interceptors}, so
 * transactions they start count too.
 *
 * @serial exclude
 */
@Interceptor
@BeanManagedTransactions
@Priority(PLATFORM_BEFORE + 250)
public class BeanManagedTransactionsInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(BeanManagedTransactionsInterceptor.class.getName());

    @Inject
    private Instance<UserTransaction> userTransaction;

    /**
     * Invokes the method, and rolls back a transaction it left open.
     *
     * @param ctx the invocation context
     * @return what the method returned
     * @throws Exception what the method threw, or an {@link EJBException} when it left a transaction open
     */
    @AroundInvoke
    public Object checkTransactionCompleted(InvocationContext ctx) throws Exception {
        Object result;

        try {
            result = ctx.proceed();
        } catch (Exception | Error throwable) {
            rollBackIncompleteTransaction(ctx, throwable);
            throw throwable;
        }

        rollBackIncompleteTransaction(ctx, null);

        return result;
    }

    private void rollBackIncompleteTransaction(InvocationContext ctx, Throwable thrown) {
        UserTransaction transaction = JakartaTransactions.getUserTransaction(userTransaction);

        try {
            if (transaction.getStatus() == STATUS_NO_TRANSACTION) {
                return;
            }

            transaction.rollback();
        } catch (Exception e) {
            logger.log(WARNING, "Could not roll back the transaction that " + ctx.getMethod() + " left open", e);
        }

        EJBException exception = new EJBException("The transaction started by " + ctx.getMethod() + " wasn't completed, and was rolled back");
        if (thrown != null) {
            exception.initCause(thrown);
        }

        throw exception;
    }
}
