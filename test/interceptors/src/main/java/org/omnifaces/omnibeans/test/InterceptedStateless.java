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

import static java.util.concurrent.TimeUnit.SECONDS;

import org.omnifaces.services.pooled.Pooled;

import jakarta.ejb.Stateless;
import jakarta.interceptor.ExcludeClassInterceptors;
import jakarta.interceptor.Interceptors;

/**
 * A stateless bean with interceptors on the class and on methods. Its pool has one instance, which a call waits for at
 * most 2 seconds, so an instance that isn't returned makes the next call fail.
 */
@Stateless
@Pooled(maxNumberOfInstances = 1, instanceLockTimeout = 2, instanceLockTimeoutUnit = SECONDS)
@Interceptors({ FirstInterceptor.class, SecondInterceptor.class })
public class InterceptedStateless {

    /**
     * Creates the bean.
     */
    public InterceptedStateless() {
    }

    /**
     * Records that it ran, with the instance it ran on.
     *
     * @param input the input, to which the interceptors append their names
     * @return "method got " and the input
     */
    @Interceptors(MethodInterceptor.class)
    public String call(String input) {
        return ran(input);
    }

    /**
     * Records that it ran, with the instance it ran on.
     *
     * @param input the input, to which the interceptors append their names
     * @return "method got " and the input
     */
    @ExcludeClassInterceptors
    @Interceptors(MethodInterceptor.class)
    public String excluded(String input) {
        return ran(input);
    }

    /**
     * Records that it ran, with the instance it ran on.
     *
     * @param input the input, to which the interceptors append their names
     * @return "method got " and the input
     */
    @ExcludeClassInterceptors
    public String excludedOnly(String input) {
        return ran(input);
    }

    /**
     * Records that it ran, with the instance it ran on.
     *
     * @param input the input, to which the interceptors append their names
     * @return "method got " and the input
     */
    @Interceptors(ShortCircuitInterceptor.class)
    public String shortCircuit(String input) {
        return ran(input);
    }

    private String ran(String input) {
        Trace.add("method target=" + Trace.id(this));
        return "method got " + input;
    }
}
