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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import jakarta.ejb.AccessTimeout;
import jakarta.ejb.IllegalLoopbackException;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

/**
 * A singleton bean with read locks on the class, write locks on some methods, and an access timeout of 500 ms on the
 * class, which a method overrides with 0 (waiting not permitted).
 */
@Singleton
@Lock(LockType.READ)
@AccessTimeout(value = 500, unit = MILLISECONDS)
public class LockedSingleton {

    @Inject
    private Latches latches;

    @Inject
    private LoopbackHelper loopbackHelper;

    /**
     * Creates the bean.
     */
    public LockedSingleton() {
    }

    /**
     * Holds a read lock until released.
     *
     * @param name the name of the call
     */
    public void read(String name) {
        latches.hold(name);
    }

    /**
     * Holds a write lock until released.
     *
     * @param name the name of the call
     */
    @Lock(LockType.WRITE)
    public void write(String name) {
        latches.hold(name);
    }

    /**
     * Returns at once, with a read lock.
     *
     * @return "read"
     */
    public String quickRead() {
        return "read";
    }

    /**
     * Returns at once, with a write lock.
     *
     * @return "written"
     */
    @Lock(LockType.WRITE)
    public String quickWrite() {
        return "written";
    }

    /**
     * With a read lock, calls a method of this bean with a write lock, through another bean.
     *
     * @return "refused" when that call throws an IllegalLoopbackException, else what it returned
     */
    public String readThenWrite() {
        try {
            return loopbackHelper.write();
        } catch (IllegalLoopbackException e) {
            return "refused";
        }
    }

    /**
     * With a read lock, calls a method of this bean with a read lock, through another bean.
     *
     * @return what that call returned
     */
    public String readThenRead() {
        return loopbackHelper.read();
    }

    /**
     * With a write lock, calls a method of this bean with a write lock, through another bean.
     *
     * @return what that call returned
     */
    @Lock(LockType.WRITE)
    public String writeThenWrite() {
        return loopbackHelper.write();
    }

    /**
     * With a write lock, calls a method of this bean with a read lock, through another bean.
     *
     * @return what that call returned
     */
    @Lock(LockType.WRITE)
    public String writeThenRead() {
        return loopbackHelper.read();
    }

    /**
     * Returns at once, with a write lock that it doesn't wait for.
     *
     * @return "written now"
     */
    @Lock(LockType.WRITE)
    @AccessTimeout(0)
    public String writeNow() {
        return "written now";
    }
}
