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
package ee.omnifish.omnibeans;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.omnifaces.services.util.CdiUtils;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Retarget
@Priority(PLATFORM_BEFORE + 10)
public class RetargetInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private BeanManager beanManager;

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    private Map<Boolean, List<Object>> interceptors = new ConcurrentHashMap<>();

    public RetargetInterceptor() {
        int a;
        a = 4;
    }

    @AroundInvoke
    public Object retarget(InvocationContext ctx) throws Exception {

        Object interceptor = interceptors.computeIfAbsent(Boolean.TRUE, e -> instantiateInterceptors(ctx))
                                         .get(0);

        AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(interceptor.getClass());

        List<AnnotatedMethod<?>> methods = annotatedType.getMethods()
                     .stream()
                     .filter(e -> e.isAnnotationPresent(AroundInvoke.class))
                     .collect(Collectors.toList());

        if (!methods.isEmpty()) {
            AnnotatedMethod<?> annotatedMethod = methods.get(0);
            Method method = annotatedMethod.getJavaMember();
            method.setAccessible(true);

            return method.invoke(interceptor, ctx);
        }

        throw new IllegalStateException();
    }

    private List<Object> instantiateInterceptors(InvocationContext ctx) {
        List<Object> interceptors = new ArrayList<>();

        Retarget retargetAnnotation = getRetargetAnnotation(ctx);

        for (Class<?> interceptorClass : retargetAnnotation.interceptorClasses()) {
            try {
                interceptors.add(interceptorClass.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        return interceptors;
    }

    private Retarget getRetargetAnnotation(InvocationContext ctx) {
        return CdiUtils.getInterceptorBindingAnnotation(ctx, beanManager, interceptedBean, Retarget.class);
    }



}