/*
 * Copyright 2022 OmniFaces
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
package org.omnifaces.omnibeans;

import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.PooledScopeEnabled;

import jakarta.ejb.Stateless;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;

public class CdiExtension implements Extension {

    public <T> void processBean(@Observes ProcessAnnotatedType<T> eventIn, BeanManager beanManager) {
        
        ProcessAnnotatedType<T> event = eventIn; // JDK8 u60+ workaround
        
        event.configureAnnotatedType()
               .filterMethods(e -> e.isAnnotationPresent(jakarta.ejb.Asynchronous.class))
               .forEach(e -> e.add(org.omnifaces.services.asynchronous.Asynchronous.Literal.INSTANCE));
        
        // Note: The below replacements are at the moment crude approximations of the
        //       Enterprise Beans annotations that are being replaced.
        
        if (event.getAnnotatedType().isAnnotationPresent(Stateless.class)) {
            event.configureAnnotatedType()
                 // TODO: add transactional support and the ability to configure
                 //       it like Stateless allows.
                 .add(Pooled.Literal.INSTANCE)
                 .add(PooledScopeEnabled.Literal.INSTANCE);
        }
        
        if (event.getAnnotatedType().isAnnotationPresent(jakarta.ejb.Singleton.class)) {
            event.configureAnnotatedType()
                  // TODO: add locking
                 .add(ApplicationScoped.Literal.INSTANCE);
        }
        
        
    }
    
}
