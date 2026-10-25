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

import java.util.Set;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.security.jacc.PolicyContext;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Puts the statements of the session beans into service when the web module starts, before its listeners run, in a
 * policy context of their own ({@link JakartaAuthorization}), and takes them out of service when it stops.
 * <p>
 * Only when the container set the web module's policy context on the thread (as Exousia does), and the application
 * has session beans. Initializers that run before this one can't call secured session beans.
 */
public class SessionBeanPolicyContextInitializer implements ServletContainerInitializer {

    /**
     * Creates the initializer. Used by the servlet container.
     */
    public SessionBeanPolicyContextInitializer() {
    }

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) {
        String webContextId = PolicyContext.getContextID();
        if (webContextId == null) {
            return;
        }

        SecurityDescriptorRegistry registry = getRegistry();
        if (registry == null || registry.isEmpty()) {
            return;
        }

        JakartaAuthorization.deploy(webContextId, registry);

        servletContext.addListener(new ServletContextListener() {
            @Override
            public void contextDestroyed(ServletContextEvent event) {
                JakartaAuthorization.undeploy(webContextId);
            }
        });
    }

    // Unless it's unused, so it's not needed (Quarkus removes unused beans)
    private static SecurityDescriptorRegistry getRegistry() {
        Instance<SecurityDescriptorRegistry> registry;
        try {
            registry = CDI.current().select(SecurityDescriptorRegistry.class);
        } catch (IllegalStateException e) {
            // No CDI container
            return null;
        }

        return registry.isResolvable() ? registry.get() : null;
    }
}
