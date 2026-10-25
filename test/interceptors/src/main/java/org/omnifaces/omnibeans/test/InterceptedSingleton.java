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

import jakarta.ejb.Singleton;
import jakarta.interceptor.Interceptors;

/**
 * A singleton bean with an interceptor on the class and one on a method.
 */
@Singleton
@Interceptors(FirstInterceptor.class)
public class InterceptedSingleton {

    /**
     * Creates the bean.
     */
    public InterceptedSingleton() {
    }

    /**
     * Records that it ran, with the instance it ran on.
     *
     * @param input the input, to which the interceptors append their names
     * @return "method got " and the input
     */
    @Interceptors(MethodInterceptor.class)
    public String call(String input) {
        Trace.add("method target=" + Trace.id(this));
        return "method got " + input;
    }
}
