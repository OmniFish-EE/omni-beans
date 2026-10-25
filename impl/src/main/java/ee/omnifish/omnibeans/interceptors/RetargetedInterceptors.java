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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;

/**
 * Runs the interceptors listed by {@code @Interceptors}, in order, as an interceptor chain of its own inside the
 * invocation of a CDI interceptor.
 * <p>
 * The interceptors are instantiated once per intercepted bean instance (the CDI interceptor's instance), with their
 * public or other no-argument constructor. They don't get injection or lifecycle callbacks. Their
 * {@link AroundInvoke} methods are invoked, those of superclasses first.
 */
final class RetargetedInterceptors {

    private final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();

    /**
     * Invokes the around-invoke methods of the given interceptor classes, then proceeds with the invocation.
     *
     * @param ctx the invocation context of the CDI interceptor
     * @param interceptorClasses the interceptor classes, in order
     * @return the result of the invocation
     * @throws Exception what the interceptors or the invocation threw
     */
    Object proceed(InvocationContext ctx, Class<?>[] interceptorClasses) throws Exception {
        List<Step> steps = new ArrayList<>();
        for (Class<?> interceptorClass : interceptorClasses) {
            Object instance = instances.computeIfAbsent(interceptorClass, RetargetedInterceptors::instantiate);
            for (Method aroundInvoke : getAroundInvokeMethods(interceptorClass)) {
                steps.add(new Step(instance, aroundInvoke));
            }
        }

        return new ChainedInvocationContext(ctx, steps, 0).proceed();
    }

    private static Object instantiate(Class<?> interceptorClass) {
        try {
            Constructor<?> constructor = interceptorClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Can't instantiate interceptor " + interceptorClass.getName(), e);
        }
    }

    /**
     * Returns the around-invoke methods of the class and its superclasses, those of superclasses first, without
     * methods that are overridden.
     */
    static List<Method> getAroundInvokeMethods(Class<?> interceptorClass) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = interceptorClass; type != null && type != Object.class; type = type.getSuperclass()) {
            hierarchy.add(0, type);
        }

        List<Method> methods = new ArrayList<>();
        for (Class<?> type : hierarchy) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(AroundInvoke.class) && !isOverridden(method, interceptorClass)) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            }
        }

        return methods;
    }

    private static boolean isOverridden(Method method, Class<?> interceptorClass) {
        if (Modifier.isPrivate(method.getModifiers())) {
            return false;
        }

        for (Class<?> type = interceptorClass; type != method.getDeclaringClass(); type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod(method.getName(), method.getParameterTypes());
                return true;
            } catch (NoSuchMethodException e) {
                // Not overridden in this class
            }
        }

        return false;
    }

    private record Step(Object instance, Method aroundInvoke) {

        Object invoke(InvocationContext ctx) throws Exception {
            try {
                return aroundInvoke.invoke(instance, ctx);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception exception) {
                    throw exception;
                }

                if (e.getCause() instanceof Error error) {
                    throw error;
                }

                throw e;
            }
        }
    }

    /**
     * The invocation context for one interceptor of the chain. Proceeding invokes the next one, or the invocation
     * itself after the last.
     */
    private static final class ChainedInvocationContext implements InvocationContext {

        private final InvocationContext delegate;
        private final List<Step> steps;
        private final int position;

        ChainedInvocationContext(InvocationContext delegate, List<Step> steps, int position) {
            this.delegate = delegate;
            this.steps = steps;
            this.position = position;
        }

        @Override
        public Object proceed() throws Exception {
            if (position < steps.size()) {
                return steps.get(position).invoke(new ChainedInvocationContext(delegate, steps, position + 1));
            }

            return delegate.proceed();
        }

        @Override
        public Object getTarget() {
            return delegate.getTarget();
        }

        @Override
        public Object getTimer() {
            return delegate.getTimer();
        }

        @Override
        public Method getMethod() {
            return delegate.getMethod();
        }

        @Override
        public Constructor<?> getConstructor() {
            return delegate.getConstructor();
        }

        @Override
        public Object[] getParameters() {
            return delegate.getParameters();
        }

        @Override
        public void setParameters(Object[] params) {
            delegate.setParameters(params);
        }

        @Override
        public Map<String, Object> getContextData() {
            return delegate.getContextData();
        }

        @Override
        public Set<Annotation> getInterceptorBindings() {
            return delegate.getInterceptorBindings();
        }

        @Override
        public String toString() {
            return "ChainedInvocationContext[" + position + "/" + steps.size() + ", " + delegate + "]";
        }
    }

    @Override
    public String toString() {
        return "RetargetedInterceptors" + Arrays.toString(instances.keySet().toArray());
    }
}
