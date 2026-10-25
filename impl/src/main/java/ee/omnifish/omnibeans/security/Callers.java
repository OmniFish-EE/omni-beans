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
package ee.omnifish.omnibeans.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;

import javax.security.auth.Subject;

/**
 * The callers of the calls of session beans that run as a role in progress on the current thread, for the beans'
 * session context: inside such a bean, its caller is still the one that called it; only the beans it calls see the
 * role.
 */
final public class Callers {

    /**
     * The caller of a session bean call.
     *
     * @param beanClass the session bean class
     * @param subject the caller
     */
    public record Caller(Class<?> beanClass, Subject subject) {
    }

    private static final ThreadLocal<Deque<Caller>> CALLERS = new ThreadLocal<>();

    private Callers() {
    }

    /**
     * Runs a session bean call for its caller.
     *
     * @param caller the caller
     * @param call the call
     * @return what the call returned
     * @throws Exception what the call threw
     */
    static Object call(Caller caller, Callable<Object> call) throws Exception {
        Deque<Caller> callers = CALLERS.get();
        if (callers == null) {
            callers = new ArrayDeque<>();
            CALLERS.set(callers);
        }

        callers.push(caller);
        try {
            return call.call();
        } finally {
            callers.pop();
            if (callers.isEmpty()) {
                CALLERS.remove();
            }
        }
    }

    /**
     * Returns the caller of the innermost session bean call on the current thread.
     *
     * @return the caller, or null outside a session bean call
     */
    public static Caller current() {
        Deque<Caller> callers = CALLERS.get();
        return callers != null ? callers.peek() : null;
    }
}
