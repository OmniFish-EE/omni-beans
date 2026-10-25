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
package ee.omnifish.omnibeans;

import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.DeclarationConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.interceptor.ExcludeClassInterceptors;
import jakarta.interceptor.Interceptors;

import java.util.List;

import ee.omnifish.omnibeans.interceptors.Retarget;
import ee.omnifish.omnibeans.interceptors.RetargetClass;

/**
 * Maps the interceptors of {@code @Interceptors} to OmniBeans' retargeted interceptor bindings. Used by {@link
 * OmniBeansExtension}.
 */
final class InterceptorMapper {

    private InterceptorMapper() {
    }

    /**
     * Maps {@link Interceptors} to {@link RetargetClass} (class) and {@link Retarget} (methods), whose interceptors run
     * after the OmniServices and transactional ones. Weld would run those of {@link Interceptors} before them, so it's
     * removed. A method with {@link ExcludeClassInterceptors} gets a {@link RetargetClass} without interceptors, which
     * overrides the class' one, as a method-level interceptor binding does.
     */
    static void mapInterceptors(ClassConfig classConfig) {
        AnnotationInfo classInterceptors = classConfig.info().annotation(Interceptors.class);
        boolean hasClassInterceptors = classInterceptors != null || classConfig.info().hasAnnotation(RetargetClass.class);
        if (classInterceptors != null && !classConfig.info().hasAnnotation(RetargetClass.class)) {
            removeInterceptors(classConfig);
            classConfig.addAnnotation(toRetarget(RetargetClass.class, classInterceptors));
        }

        for (MethodConfig method : classConfig.methods()) {
            AnnotationInfo methodInterceptors = method.info().annotation(Interceptors.class);
            if (methodInterceptors != null && !method.info().hasAnnotation(Retarget.class)) {
                removeInterceptors(method);
                method.addAnnotation(toRetarget(Retarget.class, methodInterceptors));
            }

            if (hasClassInterceptors && method.info().hasAnnotation(ExcludeClassInterceptors.class) && !method.info().hasAnnotation(RetargetClass.class)) {
                method.addAnnotation(RetargetClass.Literal.INSTANCE);
            }
        }
    }

    private static void removeInterceptors(DeclarationConfig declaration) {
        declaration.removeAnnotation(annotation -> annotation.name().equals(Interceptors.class.getName()));
    }

    private static AnnotationInfo toRetarget(Class<? extends java.lang.annotation.Annotation> retargetType, AnnotationInfo interceptors) {
        List<AnnotationMember> interceptorClasses = interceptors.value().asArray();

        return AnnotationBuilder.of(retargetType)
                .member("interceptorClasses", interceptorClasses.stream().map(AnnotationMember::asType).toArray(Type[]::new))
                .build();
    }

}
