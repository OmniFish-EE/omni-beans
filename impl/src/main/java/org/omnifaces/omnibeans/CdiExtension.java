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



import static jakarta.ejb.TransactionManagementType.CONTAINER;

import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.PooledScopeEnabled;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.inject.Inject;

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

            TransactionAttribute transactionAttribute = event.getAnnotatedType().getAnnotation(TransactionAttribute.class);
            TransactionManagement transactionManagement = event.getAnnotatedType().getAnnotation(TransactionManagement.class);

            // If there is no TransactionManagement annotation set, we default to container managed and add transaction support.
            // Otherwise only add transactions if TransactionManagement is set to CONTAINER (as opposed to BEAN).
            if (transactionManagement == null || transactionManagement.value().equals(CONTAINER)) {
                
                // If there's no TransactionAttribute set, the default is REQUIRED, which is the same
                // default used by TransactionalLiteral.
                if (transactionAttribute == null) {
                    event.configureAnnotatedType()
                         .add(TransactionalLiteral.INSTANCE);
                }
            }

        }

        if (event.getAnnotatedType().isAnnotationPresent(jakarta.ejb.Singleton.class)) {
            event.configureAnnotatedType()
                  // TODO: add locking
                 .add(ApplicationScoped.Literal.INSTANCE);
        }

        // TODO: handle attributes of the EJB annotation
        event.configureAnnotatedType()
             .filterFields(e -> shouldInjectionAnnotationBeAdded(e))
             .forEach(e -> e.add(InjectLiteral.INSTANCE));

        event.configureAnnotatedType()
             .filterMethods(e -> shouldInjectionAnnotationBeAdded(e))
             .forEach(e -> e.add(InjectLiteral.INSTANCE));

    }


   private static <X> boolean shouldInjectionAnnotationBeAdded(AnnotatedMember<? super X> field) {
       return !field.isAnnotationPresent(Inject.class) && field.isAnnotationPresent(EJB.class);
   }


}
