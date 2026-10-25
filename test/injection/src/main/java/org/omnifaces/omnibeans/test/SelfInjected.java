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
package org.omnifaces.omnibeans.test;

import static jakarta.ejb.TransactionAttributeType.REQUIRES_NEW;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * A stateless bean that injects itself, the Enterprise Beans way to have its own business methods called with their
 * container services (CDI-414). A stateless bean is {@code @Pooled}, a normal scope, so CDI allows this.
 */
@Stateless
public class SelfInjected {

    @EJB
    private SelfInjected self;

    @Inject
    private TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    /**
     * Creates the bean.
     */
    public SelfInjected() {
    }

    /**
     * Calls {@link #requiresNew()} through the injected reference to itself, in its own transaction.
     *
     * @return "new transaction" when that call ran in a transaction of its own
     */
    public String outer() {
        String outer = transactionKey();
        String inner = self.requiresNew();

        return inner.equals("null") ? "no transaction" : inner.equals(outer) ? "same transaction" : "new transaction";
    }

    /**
     * Returns the key of its transaction.
     *
     * @return the transaction key, or "null"
     */
    @TransactionAttribute(REQUIRES_NEW)
    public String requiresNew() {
        return transactionKey();
    }

    private String transactionKey() {
        return String.valueOf(transactionSynchronizationRegistry.getTransactionKey());
    }
}
