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
package ee.omnifish.omnibeans;

import static jakarta.ejb.TransactionManagementType.CONTAINER;

import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.PooledScopeEnabled;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ejb.TransactionManagement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * CDI extension that maps EJB annotations to CDI based ones.
 *
 * @author Arjan Tijms
 *
 */
public class CdiExtension implements Extension {

    <T> void processBean(@Observes ProcessAnnotatedType<T> eventIn, BeanManager beanManager) {

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
                } else {
                    event.configureAnnotatedType()
                         .add(TransactionalLiteral.of(transactionAttributeTypeToTxType(transactionAttribute.value())));
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

        event.configureAnnotatedType()
             .filterMethods(e -> shouldTransactionalAnnotationBeAdded(e))
             .forEach(e -> e.add(createTransactional(e.getAnnotated())));

   }

   private Transactional createTransactional(AnnotatedMember<?> field) {
       TransactionAttribute transactionAttribute = field.getAnnotation(TransactionAttribute.class);

       return TransactionalLiteral.of(transactionAttributeTypeToTxType(transactionAttribute.value()));
   }

   private static <X> boolean shouldInjectionAnnotationBeAdded(AnnotatedMember<? super X> field) {
       return !field.isAnnotationPresent(Inject.class) && field.isAnnotationPresent(EJB.class);
   }

   private static <X> boolean shouldTransactionalAnnotationBeAdded(AnnotatedMember<? super X> field) {
       return !field.isAnnotationPresent(Transactional.class) && field.isAnnotationPresent(TransactionAttribute.class);
   }

   private TxType transactionAttributeTypeToTxType(TransactionAttributeType transactionAttributeType) {
       switch (transactionAttributeType) {
           case MANDATORY:
               return TxType.MANDATORY;
           case NEVER:
               return TxType.NEVER;
           case NOT_SUPPORTED:
               return TxType.NOT_SUPPORTED;
           case REQUIRED:
               return TxType.REQUIRED;
           case REQUIRES_NEW:
               return TxType.REQUIRES_NEW;
           case SUPPORTS:
               return TxType.SUPPORTS;
       }

       return null;
   }

}
