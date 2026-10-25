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
package ee.omnifish.omnibeans.interceptors;

import static jakarta.interceptor.Interceptor.Priority.APPLICATION;

import java.io.Serializable;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Runs the interceptors that {@code @Interceptors} on the class of a session bean lists (see {@link RetargetClass}), at
 * application priority, i.e. after the OmniServices and transactional interceptors.
 *
 * @serial exclude
 */
@Interceptor
@RetargetClass
@Priority(APPLICATION)
public class RetargetClassInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private transient RetargetedInterceptors interceptors;

    /**
     * Creates the interceptor. Used by CDI.
     */
    public RetargetClassInterceptor() {
    }

    /**
     * Runs the listed interceptors, then proceeds with the invocation.
     *
     * @param ctx the invocation context
     * @return the result of the invocation
     * @throws Exception what the interceptors or the invocation threw
     */
    @AroundInvoke
    public Object retarget(InvocationContext ctx) throws Exception {
        RetargetClass binding = ctx.getInterceptorBinding(RetargetClass.class);
        if (binding == null) {
            throw new IllegalStateException("@" + RetargetClass.class.getSimpleName() + " not present on " + ctx.getMethod());
        }

        return getInterceptors().proceed(ctx, binding.interceptorClasses());
    }

    private synchronized RetargetedInterceptors getInterceptors() {
        if (interceptors == null) {
            interceptors = new RetargetedInterceptors();
        }

        return interceptors;
    }
}
