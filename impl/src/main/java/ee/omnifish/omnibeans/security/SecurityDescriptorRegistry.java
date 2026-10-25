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
package ee.omnifish.omnibeans.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ee.omnifish.omnibeans.security.buildtime.ApplicationRoles;

/**
 * The security descriptors of all session beans of the application, and the application's roles. Looked up per bean
 * class at each call, and translated into Jakarta Authorization statements at startup
 * ({@link SessionBeanPolicyContextInitializer}).
 */
@Singleton
public class SecurityDescriptorRegistry {

    @Inject
    @Any
    private Instance<SecurityDescriptor> securityDescriptors;

    @Inject
    private Instance<ApplicationRoles> applicationRoles;

    // Per binary name of a session bean class, its security descriptor
    private final Map<String, SecurityDescriptor> descriptors = new HashMap<>();

    private Set<String> roles = Set.of();

    /**
     * Creates the registry. Used by CDI.
     */
    public SecurityDescriptorRegistry() {
    }

    @PostConstruct
    void init() {
        for (SecurityDescriptor descriptor : securityDescriptors) {
            descriptors.put(descriptor.getBeanClass(), descriptor);
        }

        if (applicationRoles.isResolvable()) {
            roles = applicationRoles.get().getRoles();
        }
    }

    /**
     * Returns the security descriptor of a session bean.
     *
     * @param beanClass the bean class
     * @return its security descriptor, or null if it isn't a session bean
     */
    public SecurityDescriptor get(Class<?> beanClass) {
        return descriptors.get(beanClass.getName());
    }

    /**
     * Returns the security descriptors of all session beans.
     */
    Collection<SecurityDescriptor> getAll() {
        return descriptors.values();
    }

    /**
     * Returns the application's roles: all those that the annotations of its classes name.
     */
    Set<String> getRoles() {
        return roles;
    }

    /**
     * Tells whether the application has no session beans.
     */
    boolean isEmpty() {
        return descriptors.isEmpty();
    }

    /**
     * Returns the number of session beans.
     */
    int size() {
        return descriptors.size();
    }
}
