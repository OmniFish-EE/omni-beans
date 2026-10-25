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

import jakarta.enterprise.inject.Instance;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Obtains the Jakarta Transactions objects: from CDI, else from JNDI (Piranha binds them there).
 */
final public class JakartaTransactions {

    private JakartaTransactions() {
    }

    /**
     * Returns the user transaction.
     *
     * @param userTransaction the CDI instance to try first
     * @return the user transaction
     * @throws IllegalStateException when there's none
     */
    public static UserTransaction getUserTransaction(Instance<UserTransaction> userTransaction) {
        if (userTransaction.isResolvable()) {
            return userTransaction.get();
        }

        return lookup("java:comp/UserTransaction", UserTransaction.class);
    }

    /**
     * Returns the transaction synchronization registry.
     *
     * @param transactionSynchronizationRegistry the CDI instance to try first
     * @return the transaction synchronization registry
     * @throws IllegalStateException when there's none
     */
    public static TransactionSynchronizationRegistry getTransactionSynchronizationRegistry(Instance<TransactionSynchronizationRegistry> transactionSynchronizationRegistry) {
        if (transactionSynchronizationRegistry.isResolvable()) {
            return transactionSynchronizationRegistry.get();
        }

        return lookup("java:comp/TransactionSynchronizationRegistry", TransactionSynchronizationRegistry.class);
    }

    private static <T> T lookup(String name, Class<T> type) {
        try {
            return type.cast(new InitialContext().lookup(name));
        } catch (NamingException | ClassCastException e) {
            throw new IllegalStateException("No Jakarta Transactions " + type.getSimpleName() + " available", e);
        }
    }
}
