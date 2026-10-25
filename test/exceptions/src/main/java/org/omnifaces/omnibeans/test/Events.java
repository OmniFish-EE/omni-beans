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

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Records what happened, such as instances being destroyed.
 */
@ApplicationScoped
public class Events {

    private final List<String> events = new ArrayList<>();

    /**
     * Creates the bean.
     */
    public Events() {
    }

    /**
     * Records an event.
     *
     * @param event the event
     */
    public synchronized void add(String event) {
        events.add(event);
    }

    /**
     * Returns the events recorded so far.
     *
     * @return the events
     */
    public synchronized List<String> events() {
        return new ArrayList<>(events);
    }

    /**
     * Forgets the events recorded so far.
     */
    public synchronized void clear() {
        events.clear();
    }

    /**
     * Returns an identification of an instance.
     *
     * @param instance the instance
     * @return the identification
     */
    public static String id(Object instance) {
        return Integer.toHexString(System.identityHashCode(instance));
    }
}
