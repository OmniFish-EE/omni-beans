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

import static jakarta.ejb.TransactionAttributeType.MANDATORY;
import static jakarta.ejb.TransactionAttributeType.NEVER;
import static jakarta.ejb.TransactionAttributeType.NOT_SUPPORTED;
import static jakarta.ejb.TransactionAttributeType.REQUIRES_NEW;
import static jakarta.ejb.TransactionAttributeType.SUPPORTS;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import jakarta.annotation.PreDestroy;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * A stateless bean with Container Managed Transactions: the default transaction attribute (REQUIRED), and the others on
 * methods. Each method returns the key of the transaction it runs in, and records how that transaction completes, as
 * "&lt;label&gt; completed status=&lt;status&gt;".
 */
@Stateless
public class StatelessContainerManaged {

    @Inject
    private Events events;

    @Inject
    private TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    /**
     * Creates the bean.
     */
    public StatelessContainerManaged() {
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
     * Runs with the default transaction attribute, REQUIRED.
     *
     * @param label the label of the completion event
     * @return the transaction key, or "null"
     */
    public String required(String label) {
        return record(label);
    }

    /**
     * Runs with REQUIRES_NEW.
     *
     * @param label the label of the completion event
     * @return the transaction key, or "null"
     */
    @TransactionAttribute(REQUIRES_NEW)
    public String requiresNew(String label) {
        return record(label);
    }

    /**
     * Runs with MANDATORY.
     *
     * @param label the label of the completion event
     * @return the transaction key, or "null"
     */
    @TransactionAttribute(MANDATORY)
    public String mandatory(String label) {
        return record(label);
    }

    /**
     * Runs with SUPPORTS.
     *
     * @param label the label of the completion event
     * @return the transaction key, or "null"
     */
    @TransactionAttribute(SUPPORTS)
    public String supports(String label) {
        return record(label);
    }

    /**
     * Runs with NOT_SUPPORTED.
     *
     * @param label the label of the completion event
     * @return the transaction key, or "null"
     */
    @TransactionAttribute(NOT_SUPPORTED)
    public String notSupported(String label) {
        return record(label);
    }

    /**
     * Runs with NEVER.
     *
     * @param label the label of the completion event
     * @return the transaction key, or "null"
     */
    @TransactionAttribute(NEVER)
    public String never(String label) {
        return record(label);
    }

    /**
     * Runs asynchronously, with the default transaction attribute.
     *
     * @param label the label of the completion event
     * @return the transaction key, or "null"
     */
    @Asynchronous
    public Future<String> requiredAsync(String label) {
        return CompletableFuture.completedFuture(record(label));
    }

    /**
     * Runs asynchronously, with the default transaction attribute, and returns a future that fails a while later.
     *
     * @param label the label of the completion event
     * @param checked whether it fails with a checked exception, else with an unchecked one
     * @return the future
     * @throws FutureProblem never directly, but a checked exception that the future fails with must be declared (as an
     *     application exception must be)
     */
    @Asynchronous
    public Future<String> failLater(String label, boolean checked) throws FutureProblem {
        record(label);

        CompletableFuture<String> result = new CompletableFuture<>();
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            result.completeExceptionally(checked ? new FutureProblem() : new IllegalStateException("Unchecked failure of the future"));
        }).start();

        return result;
    }

    private String record(String label) {
        Object key = transactionSynchronizationRegistry.getTransactionKey();
        if (key != null) {
            transactionSynchronizationRegistry.registerInterposedSynchronization(new CompletionRecorder(events, label));
        }

        return String.valueOf(key);
    }
}
