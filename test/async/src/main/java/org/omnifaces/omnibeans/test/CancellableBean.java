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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import jakarta.ejb.Asynchronous;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * A stateless bean with an asynchronous method that waits until its caller cancels it.
 */
@Stateless
public class CancellableBean {

    /** Counted down when the asynchronous method started */
    public static final CountDownLatch STARTED = new CountDownLatch(1);

    @Inject
    private SessionContext sessionContext;

    /**
     * Creates the bean.
     */
    public CancellableBean() {
    }

    /**
     * Waits at most 10 seconds until {@link SessionContext#wasCancelCalled()} returns true.
     *
     * @return whether it did, and whether this thread was interrupted
     * @throws InterruptedException never, as the thread isn't interrupted
     */
    @Asynchronous
    public Future<String> awaitCancel() throws InterruptedException {
        STARTED.countDown();

        long deadline = System.currentTimeMillis() + 10_000;
        while (!sessionContext.wasCancelCalled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        return CompletableFuture.completedFuture("wasCancelCalled: " + sessionContext.wasCancelCalled() + ", interrupted: " + Thread.currentThread().isInterrupted());
    }
}
