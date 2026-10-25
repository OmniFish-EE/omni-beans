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

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;

/**
 * Returns a result without proceeding, so the method doesn't run.
 */
public class ShortCircuitInterceptor {

    /**
     * Creates the interceptor.
     */
    public ShortCircuitInterceptor() {
    }

    /**
     * Records the invocation and returns without proceeding.
     *
     * @param ctx the invocation context
     * @return "short-circuited"
     */
    @AroundInvoke
    public Object intercept(InvocationContext ctx) {
        Trace.add("ShortCircuit target=" + Trace.id(ctx.getTarget()));
        return "short-circuited";
    }
}
