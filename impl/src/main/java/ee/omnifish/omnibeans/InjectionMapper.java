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

import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBContext;
import jakarta.ejb.SessionContext;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.DeclarationConfig;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Maps Enterprise Beans injection ({@code @EJB}, and {@code @Resource} of the session context) to CDI injection. Used
 * by {@link OmniBeansExtension}.
 */
final class InjectionMapper {

    private InjectionMapper() {
    }

    /**
     * Maps {@link EJB} on fields and setters to {@link Inject}. With {@code EJB.beanName()}, the injection point gets that
     * name ({@link Named}), which is the name of the session bean.
     */
    static void mapInjection(ClassConfig classConfig) {
        for (FieldConfig field : classConfig.fields()) {
            AnnotationInfo ejb = field.info().annotation(EJB.class);
            if (ejb != null && !field.info().hasAnnotation(Inject.class)) {
                field.addAnnotation(InjectLiteral.INSTANCE);

                String beanName = ejb.member("beanName").asString();
                if (!beanName.isEmpty() && !field.info().hasAnnotation(Named.class)) {
                    field.addAnnotation(NamedLiteral.of(beanName));
                }
            }

            if (isContextResource(field.info().annotation(Resource.class), field.info().type())) {
                mapContextResource(field);
            }
        }

        for (MethodConfig method : classConfig.methods()) {
            AnnotationInfo ejb = method.info().annotation(EJB.class);
            if (ejb != null && !method.info().hasAnnotation(Inject.class)) {
                method.addAnnotation(InjectLiteral.INSTANCE);

                String beanName = ejb.member("beanName").asString();
                if (!beanName.isEmpty() && !method.parameters().isEmpty() && !method.parameters().get(0).info().hasAnnotation(Named.class)) {
                    method.parameters().get(0).addAnnotation(NamedLiteral.of(beanName));
                }
            }

            if (method.parameters().size() == 1 && isContextResource(method.info().annotation(Resource.class), method.parameters().get(0).info().type())) {
                mapContextResource(method);
            }
        }
    }

 // The session context is a bean of this library, so it's injected instead of looked up
    private static boolean isContextResource(AnnotationInfo resource, Type type) {
        if (resource == null || !type.isClass()) {
            return false;
        }

        String typeName = type.asClass().declaration().name();
        return typeName.equals(SessionContext.class.getName()) || typeName.equals(EJBContext.class.getName());
    }

    private static void mapContextResource(DeclarationConfig declaration) {
        declaration.removeAnnotation(annotation -> annotation.name().equals(Resource.class.getName()));

        if (!declaration.info().hasAnnotation(Inject.class)) {
            declaration.addAnnotation(InjectLiteral.INSTANCE);
        }
    }

}
