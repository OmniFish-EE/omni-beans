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
package ee.omnifish.omnibeans;

import static jakarta.ejb.TransactionManagementType.CONTAINER;

import java.util.concurrent.TimeUnit;

import org.omnifaces.services.lock.Lock;
import org.omnifaces.services.lock.Lock.TimeoutType;
import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.PooledScopeEnabled;

import jakarta.ejb.AccessTimeout;
import jakarta.ejb.EJB;
import jakarta.ejb.LockType;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ejb.TransactionManagement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/**
 * CDI extension that maps EJB annotations to CDI based ones.
 *
 * @author Arjan Tijms
 *
 */
public class CdiExtension implements Extension {

    public void register(@Observes BeforeBeanDiscovery beforeBean, BeanManager beanManager) {
        addAnnotatedTypes(beforeBean, beanManager,
            SessionContextImpl.class,
            Retarget.class,
            RetargetInterceptor.class,
            RetargetClass.class,
            RetargetClassInterceptor.class
        );
    }

    <T> void processBean(@Observes ProcessAnnotatedType<T> eventIn, BeanManager beanManager) {

        ProcessAnnotatedType<T> event = eventIn; // JDK8 u60+ workaround

        event.configureAnnotatedType()
               .filterMethods(e -> e.isAnnotationPresent(jakarta.ejb.Asynchronous.class))
               .forEach(e -> e.add(org.omnifaces.services.asynchronous.Asynchronous.Literal.INSTANCE));


        // Note: The below replacements are at the moment crude approximations of the
        //       Enterprise Beans annotations that are being replaced.

        if (event.getAnnotatedType().isAnnotationPresent(Stateless.class)) {
            event.configureAnnotatedType()
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
            AccessTimeout classAccessTimeout = event.getAnnotatedType().getAnnotation(AccessTimeout.class);

            event.configureAnnotatedType()
                 .add(NamedLiteral.of(event.getAnnotatedType().getJavaClass().getSimpleName())) // TODO: set exact name
                 .add(ApplicationScoped.Literal.INSTANCE)
                 ;

            if (shouldLockAnnotationBeAdded(event.getAnnotatedType())) {
                event.configureAnnotatedType().add(createLock(classAccessTimeout, event.getAnnotatedType()));
            } else {
                event.configureAnnotatedType().add(Lock.Literal.INSTANCE);
            }


            // @Interceptors -> @Retarget
            // This is done since @Interceptors always come first, and we need @Lock before that.
            // We therefore move them up a few places in the interceptor chain.
            if (shouldRetargetInterceptors(event.getAnnotatedType())) {
                retargetInterceptors(event.configureAnnotatedType());
            }

            event.configureAnnotatedType()
                 .filterMethods(e -> shouldRetargetInterceptors(e))
                 .forEach(e -> retargetInterceptors(e));

            // @ejb.lock -> @cdi.lock
            event.configureAnnotatedType()
                 .filterMethods(e -> shouldLockAnnotationBeAdded(e))
                 .forEach(e -> e.add(createLock(classAccessTimeout, e.getAnnotated())));
        }



        // @EJB -> @Inject fields
        event.configureAnnotatedType()
             .filterFields(e -> shouldInjectionAnnotationBeAdded(e))
             .forEach(e -> e.add(InjectLiteral.INSTANCE));

        // @EJB -> @Inject methods
        event.configureAnnotatedType()
             .filterMethods(e -> shouldInjectionAnnotationBeAdded(e))
             .forEach(e -> addInject(e));

        // @TransactionAttribute -> @Transactional
        event.configureAnnotatedType()
             .filterMethods(e -> shouldTransactionalAnnotationBeAdded(e))
             .forEach(e -> e.add(createTransactional(e.getAnnotated())));

   }



    private void retargetInterceptors(AnnotatedTypeConfigurator<?> e) {
        Interceptors interceptorsAnnotation = e.getAnnotated().getAnnotation(Interceptors.class);
        e.remove(a -> a.annotationType().equals(Interceptors.class));
        e.add(RetargetClass.Literal.of(interceptorsAnnotation.value()));
    }

    private void retargetInterceptors(AnnotatedMethodConfigurator<?> e) {
        Interceptors interceptorsAnnotation = e.getAnnotated().getAnnotation(Interceptors.class);
        e.remove(a -> a.annotationType().equals(Interceptors.class));
        e.add(Retarget.Literal.of(interceptorsAnnotation.value()));
    }

   /**
    * This method maps a method like the following:
    *
    * <p>
    * {@code
    * @EJB(beanName = "SomeBean")
    * public void setSomeBean(SomeBeanInterface bean) { }
    * }
    *
    * <p>
    * to
    *
    * <p>
    * {@code
    * @Inject
    * public void setSomeBean(@Named("SomeBean" SomeBeanInterface bean) { }
    * }
    *
    *
    * @param configurator
    */
   private void addInject(AnnotatedMethodConfigurator<?> configurator) {
       EJB ejb = configurator.getAnnotated().getAnnotation(EJB.class);
       if (!"".equals(ejb.beanName())) {
           configurator.params().stream()
                       .findFirst()
                       .ifPresent(e -> e.add(NamedLiteral.of(ejb.beanName())));
       }

       configurator.add(InjectLiteral.INSTANCE);
   }

   private Transactional createTransactional(AnnotatedMember<?> field) {
       TransactionAttribute transactionAttribute = field.getAnnotation(TransactionAttribute.class);

       return TransactionalLiteral.of(transactionAttributeTypeToTxType(transactionAttribute.value()));
   }

   private static org.omnifaces.services.lock.Lock createLock(AccessTimeout classAccessTimeout, Annotated annotated) {
       jakarta.ejb.Lock ejbLock =  annotated.getAnnotation(jakarta.ejb.Lock.class);
       AccessTimeout annotatedAccessTimeout = annotated.getAnnotation(AccessTimeout.class);

       AccessTimeout accessTimeout = annotatedAccessTimeout != null? annotatedAccessTimeout : classAccessTimeout;

       org.omnifaces.services.lock.Lock defaultOmniLock = Lock.Literal.INSTANCE;

       return Lock.Literal.of(
           lockTypeToLockDotType(ejbLock.value()),
           accessTimeoutToTimeoutType(accessTimeout, defaultOmniLock),
           accessTimeoutToDuration(accessTimeout, defaultOmniLock),
           accessTimeoutToTimeUnit(accessTimeout, defaultOmniLock));

   }

   private static <X> boolean shouldInjectionAnnotationBeAdded(Annotated annotated) {
       return !annotated.isAnnotationPresent(Inject.class) && annotated.isAnnotationPresent(EJB.class);
   }

   private static <X> boolean shouldTransactionalAnnotationBeAdded(Annotated annotated) {
       return !annotated.isAnnotationPresent(Transactional.class) && annotated.isAnnotationPresent(TransactionAttribute.class);
   }

   private static <X> boolean shouldRetargetInterceptors(Annotated annotated) {
       return !annotated.isAnnotationPresent(Retarget.class) && annotated.isAnnotationPresent(Interceptors.class);
   }

   private static <X> boolean shouldLockAnnotationBeAdded(Annotated field) {
       return !field.isAnnotationPresent(org.omnifaces.services.lock.Lock.class) && field.isAnnotationPresent(jakarta.ejb.Lock.class);
   }

   private static Lock.Type lockTypeToLockDotType(LockType lockType) {
       switch (lockType) {
           case READ:
               return Lock.Type.READ;
           case WRITE:
               return Lock.Type.WRITE;
       }

       return null;
   }

   private static TxType transactionAttributeTypeToTxType(TransactionAttributeType transactionAttributeType) {
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

   private static TimeoutType accessTimeoutToTimeoutType(AccessTimeout accessTimeout, org.omnifaces.services.lock.Lock defaultOmniLock) {
       if (accessTimeout == null) {
           return defaultOmniLock.timeoutType();
       }

       if (accessTimeout.value() > 0) {
           return TimeoutType.TIMEOUT;
       }

       if (accessTimeout.value() == 0) {
           return TimeoutType.NOT_PERMITTED;
       }

       return TimeoutType.INDEFINITTE;
   }

   private static long accessTimeoutToDuration(AccessTimeout accessTimeout, org.omnifaces.services.lock.Lock defaultOmniLock) {
       if (accessTimeout == null) {
           return defaultOmniLock.accessTimeout();
       }

       return accessTimeout.value();
   }

   private static TimeUnit accessTimeoutToTimeUnit(AccessTimeout accessTimeout, org.omnifaces.services.lock.Lock defaultOmniLock) {
       if (accessTimeout == null) {
           return defaultOmniLock.unit();
       }

       return accessTimeout.unit();
   }




   public static void addAnnotatedTypes(BeforeBeanDiscovery beforeBean, BeanManager beanManager, Class<?>... types) {
       for (Class<?> type : types) {
           beforeBean.addAnnotatedType(beanManager.createAnnotatedType(type), "OmniBeans " + type.getName());
       }
   }

}
