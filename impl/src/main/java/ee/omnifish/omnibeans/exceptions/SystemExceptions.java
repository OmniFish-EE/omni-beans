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
package ee.omnifish.omnibeans.exceptions;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

/**
 * Binds the {@link SystemExceptionInterceptor} to a session bean, which OmniBeans maps to this.
 */
@InterceptorBinding
@Target(TYPE)
@Retention(RUNTIME)
public @interface SystemExceptions {

    /**
     * Supports inline instantiation of the {@link SystemExceptions} annotation.
     *
     * @serial exclude
     */
    public static final class Literal extends AnnotationLiteral<SystemExceptions> implements SystemExceptions {

        private static final long serialVersionUID = 1L;

        /**
         * Instance of the {@link SystemExceptions} annotation.
         */
        public static final Literal INSTANCE = new Literal();

        private Literal() {
        }
    }
}
