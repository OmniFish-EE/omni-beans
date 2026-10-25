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

import jakarta.annotation.PreDestroy;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

/**
 * A singleton bean that throws a system exception.
 */
@Singleton
public class SingletonThrower {

    @Inject
    private Events events;

    /**
     * Creates the bean.
     */
    public SingletonThrower() {
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
     * Throws a system exception.
     */
    public void systemException() {
        throw new IllegalStateException("system");
    }
}
