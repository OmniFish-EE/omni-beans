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
package org.omnifaces.omnibeans.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import jakarta.annotation.PreDestroy;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

/**
 * A stateless bean with Bean Managed Transactions, with methods that complete their transaction and methods that
 * don't.
 */
@Stateless
@TransactionManagement(TransactionManagementType.BEAN)
public class StatelessBeanManaged {

    @Inject
    private Events events;

    @Inject
    private SessionContext sessionContext;

    @Inject
    private TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    /**
     * Creates the bean.
     */
    public StatelessBeanManaged() {
    }

    /**
     * Records that this instance is destroyed.
     */
    @PreDestroy
    public void destroyed() {
        events.add("destroyed " + Events.id(this));
    }

    /**
     * Returns the identification of the instance the call ran on.
     *
     * @return the identification
     */
    public String instance() {
        return Events.id(this);
    }

    /**
     * Returns the key of the transaction the call runs in, if any.
     *
     * @return the transaction key, or "null"
     */
    public String transactionKey() {
        return String.valueOf(transactionSynchronizationRegistry.getTransactionKey());
    }

    /**
     * Starts a transaction, calls one of its own business methods on this instance, and commits the transaction.
     *
     * @return "intercepted" when that call ran without the transaction, because its interceptors ran and
     *     {@code @Transactional(NOT_SUPPORTED)} suspended it (ArC), "not intercepted" when it ran in it, as with an
     *     Enterprise Beans container (Weld)
     * @throws Exception when the transaction fails
     */
    public String selfCall() throws Exception {
        UserTransaction transaction = sessionContext.getUserTransaction();
        transaction.begin();
        try {
            String own = currentTransactionKey();
            String inSelfCall = transactionKey();

            return inSelfCall.equals(own) ? "not intercepted" : inSelfCall.equals("null") ? "intercepted" : "in transaction " + inSelfCall;
        } finally {
            transaction.commit();
        }
    }

    private String currentTransactionKey() {
        return String.valueOf(transactionSynchronizationRegistry.getTransactionKey());
    }

    /**
     * Starts a transaction and commits it.
     *
     * @throws Exception when the transaction fails
     */
    public void commit() throws Exception {
        UserTransaction transaction = sessionContext.getUserTransaction();
        transaction.begin();
        transactionSynchronizationRegistry.registerInterposedSynchronization(new CompletionRecorder(events, "commit"));
        transaction.commit();
    }

    /**
     * Returns the key of the transaction the call starts in, then starts a transaction of its own and commits it.
     *
     * @return "before=&lt;key or null&gt; own=&lt;key&gt;"
     * @throws Exception when the transaction fails
     */
    public String ownTransaction() throws Exception {
        // Not transactionKey(): on Quarkus, calling a method on this instance runs its interceptors too (ArC intercepts
        // self-invocation, Weld doesn't), so with Bean Managed Transactions, @Transactional(NOT_SUPPORTED) suspends the
        // transaction (see selfCall())
        String before = currentTransactionKey();

        UserTransaction transaction = sessionContext.getUserTransaction();
        transaction.begin();
        String own = currentTransactionKey();
        transactionSynchronizationRegistry.registerInterposedSynchronization(new CompletionRecorder(events, "own"));
        transaction.commit();

        return "before=" + before + " own=" + own;
    }

    /**
     * Starts a transaction and returns without completing it.
     *
     * @throws Exception when the transaction can't be started
     */
    public void leak() throws Exception {
        sessionContext.getUserTransaction().begin();
        transactionSynchronizationRegistry.registerInterposedSynchronization(new CompletionRecorder(events, "leak"));
    }

    /**
     * Starts a transaction and returns without completing it, asynchronously.
     *
     * @return a completed future
     * @throws Exception when the transaction can't be started
     */
    @Asynchronous
    public Future<String> leakAsync() throws Exception {
        sessionContext.getUserTransaction().begin();
        transactionSynchronizationRegistry.registerInterposedSynchronization(new CompletionRecorder(events, "async leak"));
        return CompletableFuture.completedFuture("leaked");
    }

    /**
     * Returns the key of the transaction the call runs in, if any, asynchronously.
     *
     * @return the transaction key, or "null"
     */
    @Asynchronous
    public Future<String> transactionKeyAsync() {
        return CompletableFuture.completedFuture(String.valueOf(transactionSynchronizationRegistry.getTransactionKey()));
    }
}
