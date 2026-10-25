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
package ee.omnifish.omnibeans.security.buildtime;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import ee.omnifish.omnibeans.security.SecurityDescriptor;
import jakarta.inject.Qualifier;

/**
 * Qualifies the {@link SecurityDescriptor} of a session bean with the binary name of the bean class.
 */
@Qualifier
@Target({ TYPE, METHOD, FIELD, PARAMETER })
@Retention(RUNTIME)
public @interface SessionBeanClass {

    /**
     * Returns the binary name of the session bean class.
     *
     * @return the binary name of the session bean class
     */
    String value();

    /**
     * Supports inline instantiation of the {@link SessionBeanClass} qualifier.
     *
     * @serial exclude
     */
    public static final class Literal extends AnnotationLiteral<SessionBeanClass> implements SessionBeanClass {

        private static final long serialVersionUID = 1L;

        private final String value;

        private Literal(String value) {
            this.value = value;
        }

        /**
         * Returns the qualifier for a session bean class.
         *
         * @param value the binary name of the session bean class
         * @return the qualifier
         */
        public static Literal of(String value) {
            return new Literal(value);
        }

        @Override
        public String value() {
            return value;
        }
    }
}
