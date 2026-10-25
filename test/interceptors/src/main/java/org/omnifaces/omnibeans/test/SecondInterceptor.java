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
 * The second class-level interceptor, with a superclass that has an around-invoke method too.
 */
public class SecondInterceptor extends BaseInterceptor {

    /**
     * Creates the interceptor.
     */
    public SecondInterceptor() {
    }

    /**
     * Records the invocation and proceeds.
     *
     * @param ctx the invocation context
     * @return the result of the invocation
     * @throws Exception what the invocation threw
     */
    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        return Interception.proceed("Second", ctx);
    }
}
