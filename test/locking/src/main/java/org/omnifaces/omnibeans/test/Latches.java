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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Lets a call hold its lock until the probe releases it.
 */
@ApplicationScoped
public class Latches {

    private final Map<String, CountDownLatch> entered = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> released = new ConcurrentHashMap<>();

    /**
     * Creates the bean.
     */
    public Latches() {
    }

    /**
     * Records that the call with the given name entered, and waits until it's released, at most 30 seconds.
     *
     * @param name the name of the call
     */
    public void hold(String name) {
        latch(entered, name).countDown();
        await(latch(released, name), 30_000);
    }

    /**
     * Waits until the call with the given name entered.
     *
     * @param name the name of the call
     * @param millis how long to wait at most
     * @return whether it entered
     */
    public boolean awaitEntered(String name, long millis) {
        return await(latch(entered, name), millis);
    }

    /**
     * Releases the call with the given name.
     *
     * @param name the name of the call
     */
    public void release(String name) {
        latch(released, name).countDown();
    }

    private static CountDownLatch latch(Map<String, CountDownLatch> latches, String name) {
        return latches.computeIfAbsent(name, key -> new CountDownLatch(1));
    }

    private static boolean await(CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
