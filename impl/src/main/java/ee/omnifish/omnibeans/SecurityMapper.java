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

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.RunAs;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;

import java.util.Set;

import ee.omnifish.omnibeans.security.SessionBeanRunAs;
import ee.omnifish.omnibeans.security.SessionBeanRunAsInterceptor;
import ee.omnifish.omnibeans.security.SessionBeanSecurity;
import ee.omnifish.omnibeans.security.SessionBeanSecurityInterceptor;
import ee.omnifish.omnibeans.security.buildtime.SecurityData;

/**
 * Binds OmniBeans' security interceptors to the session beans that their annotations secure. Used by {@link
 * OmniBeansExtension}.
 */
final class SecurityMapper {

    private SecurityMapper() {
    }

    /**
     * The annotations that grant the business methods of a session bean, which Jakarta Authorization handles. Quarkus
     * binds interceptors of its own to them, which check roles instead.
     */
    private static final Set<String> METHOD_PERMISSION_ANNOTATIONS = Set.of(
        RolesAllowed.class.getName(),
        PermitAll.class.getName(),
        DenyAll.class.getName());

    /**
     * Binds the {@link SessionBeanSecurityInterceptor} to a session bean that its annotations secure, and the
     * {@link SessionBeanRunAsInterceptor} to one with {@link RunAs}.
     *
     * <p>
     * Their security was read at build time, so the annotations that Quarkus binds interceptors of its own to
     * (which check roles instead) are removed from the class and its methods. Those of superclasses stay.
     */
    static void mapSecurity(ClassConfig classConfig, SecurityData security) {
        if (security == null || !security.secured()) {
            return;
        }

        classConfig.addAnnotation(SessionBeanSecurity.Literal.INSTANCE);
        if (security.runAs() != null) {
            classConfig.addAnnotation(SessionBeanRunAs.Literal.INSTANCE);
        }

        classConfig.removeAnnotation(annotation -> METHOD_PERMISSION_ANNOTATIONS.contains(annotation.name()));

        // The methods include inherited ones, whose annotations are those of the superclass itself
        String className = classConfig.info().name();
        for (MethodConfig method : classConfig.methods()) {
            if (method.info().declaringClass().name().equals(className)) {
                method.removeAnnotation(annotation -> METHOD_PERMISSION_ANNOTATIONS.contains(annotation.name()));
            }
        }
    }

}
