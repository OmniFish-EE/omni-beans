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

import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;

import static ee.omnifish.omnibeans.Utils.isMappable;

/**
 * Maps Enterprise Beans asynchronous methods to OmniServices ones. Used by {@link OmniBeansExtension}.
 */
final class AsynchronousMapper {

    private AsynchronousMapper() {
    }

    /**
     * Maps {@link jakarta.ejb.Asynchronous} on the class or its methods to OmniServices asynchronous methods.
     */
    static void mapAsynchronous(ClassConfig classConfig) {
        if (isMappable(classConfig.info(), jakarta.ejb.Asynchronous.class, org.omnifaces.services.asynchronous.Asynchronous.class)) {
            classConfig.addAnnotation(org.omnifaces.services.asynchronous.Asynchronous.Literal.INSTANCE);
        }

        for (MethodConfig method : classConfig.methods()) {
            if (isMappable(method.info(), jakarta.ejb.Asynchronous.class, org.omnifaces.services.asynchronous.Asynchronous.class)) {
                method.addAnnotation(org.omnifaces.services.asynchronous.Asynchronous.Literal.INSTANCE);
            }
        }
    }
}
