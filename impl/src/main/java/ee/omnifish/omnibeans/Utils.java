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

import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;

/**
 * Helpers for {@link OmniBeansExtension} and its mappers.
 */
final class Utils {

    private Utils() {
    }

    static boolean isMappable(DeclarationInfo declaration, Class<? extends java.lang.annotation.Annotation> from, Class<? extends java.lang.annotation.Annotation> to) {
        return declaration.hasAnnotation(from) && !declaration.hasAnnotation(to);
    }

    static boolean isAvailable(String className) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Class.forName(className, false, classLoader != null ? classLoader : OmniBeansExtension.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    // The language model includes inherited annotations; one the superclass has is taken to be inherited
    static boolean isInherited(ClassInfo classInfo, Class<? extends java.lang.annotation.Annotation> annotation) {
        ClassInfo superclass = classInfo.superClassDeclaration();
        return superclass != null && superclass.hasAnnotation(annotation);
    }

    static boolean isEnumMember(AnnotationInfo annotation, String constant) {
        return annotation != null && annotation.value().asEnumConstant().equals(constant);
    }
}
