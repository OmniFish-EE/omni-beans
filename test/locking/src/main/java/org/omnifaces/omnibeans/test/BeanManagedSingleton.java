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

import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

/**
 * A singleton bean with Bean Managed Concurrency, so without locks (container managed, it would have write locks).
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class BeanManagedSingleton {

    @Inject
    private Latches latches;

    /**
     * Creates the bean.
     */
    public BeanManagedSingleton() {
    }

    /**
     * Runs until released.
     *
     * @param name the name of the call
     */
    public void hold(String name) {
        latches.hold(name);
    }

    /**
     * Returns at once.
     *
     * @return "quick"
     */
    public String quick() {
        return "quick";
    }
}
