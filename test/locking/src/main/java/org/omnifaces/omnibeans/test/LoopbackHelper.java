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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Calls the {@link LockedSingleton} back, through its client proxy.
 */
@ApplicationScoped
public class LoopbackHelper {

    @Inject
    private LockedSingleton lockedSingleton;

    /**
     * Creates the bean.
     */
    public LoopbackHelper() {
    }

    /**
     * Calls a method of the singleton with a read lock.
     *
     * @return what it returned
     */
    public String read() {
        return lockedSingleton.quickRead();
    }

    /**
     * Calls a method of the singleton with a write lock.
     *
     * @return what it returned
     */
    public String write() {
        return lockedSingleton.quickWrite();
    }
}
