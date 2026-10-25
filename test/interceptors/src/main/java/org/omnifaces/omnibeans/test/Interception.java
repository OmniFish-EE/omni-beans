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

import java.util.ArrayList;
import java.util.List;

import jakarta.interceptor.InvocationContext;

/**
 * What each test interceptor does: it records itself, with the interceptors before it (from the context data) and the
 * target; adds itself to the context data; appends its name to the method's String parameter; and proceeds.
 */
final class Interception {

    private static final String CHAIN = "omnibeans.test.chain";

    private Interception() {
    }

    @SuppressWarnings("unchecked")
    static Object proceed(String name, InvocationContext ctx) throws Exception {
        List<String> chain = (List<String>) ctx.getContextData().computeIfAbsent(CHAIN, key -> new ArrayList<String>());
        Trace.add(name + " chain=" + chain + " target=" + Trace.id(ctx.getTarget()));
        chain.add(name);

        Object[] parameters = ctx.getParameters();
        if (parameters.length > 0 && parameters[0] instanceof String parameter) {
            parameters[0] = parameter + ">" + name;
            ctx.setParameters(parameters);
        }

        return ctx.proceed();
    }
}
