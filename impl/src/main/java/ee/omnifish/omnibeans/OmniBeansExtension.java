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

import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBContext;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionManagement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.omnifaces.services.lock.Lock;
import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.PooledScopeEnabled;

import ee.omnifish.omnibeans.exceptions.SystemExceptionInterceptor;
import ee.omnifish.omnibeans.exceptions.SystemExceptions;
import ee.omnifish.omnibeans.interceptors.RetargetClassInterceptor;
import ee.omnifish.omnibeans.interceptors.RetargetInterceptor;
import ee.omnifish.omnibeans.security.SecurityDescriptor;
import ee.omnifish.omnibeans.security.SecurityDescriptorRegistry;
import ee.omnifish.omnibeans.security.SessionBeanPolicyContextInitializer;
import ee.omnifish.omnibeans.security.SessionBeanRunAsInterceptor;
import ee.omnifish.omnibeans.security.SessionBeanSecurityInterceptor;
import ee.omnifish.omnibeans.security.buildtime.ApplicationRoles;
import ee.omnifish.omnibeans.security.buildtime.ApplicationRolesCreator;
import ee.omnifish.omnibeans.security.buildtime.SecurityData;
import ee.omnifish.omnibeans.security.buildtime.SecurityDescriptorCreator;
import ee.omnifish.omnibeans.security.buildtime.SessionBeanClass;
import ee.omnifish.omnibeans.transactions.BeanManagedTransactionsInterceptor;

import static ee.omnifish.omnibeans.AsynchronousMapper.mapAsynchronous;
import static ee.omnifish.omnibeans.InjectionMapper.mapInjection;
import static ee.omnifish.omnibeans.InterceptorMapper.mapInterceptors;
import static ee.omnifish.omnibeans.LockMapper.mapLocks;
import static ee.omnifish.omnibeans.SecurityMapper.mapSecurity;
import static ee.omnifish.omnibeans.TransactionMapper.mapTransactionAttribute;
import static ee.omnifish.omnibeans.TransactionMapper.mapTransactions;
import static ee.omnifish.omnibeans.Utils.isAvailable;
import static ee.omnifish.omnibeans.Utils.isEnumMember;
import static ee.omnifish.omnibeans.Utils.isInherited;
import static ee.omnifish.omnibeans.security.buildtime.SecurityData.isSessionBean;
import static jakarta.interceptor.Interceptor.Priority.APPLICATION;

/**
 * Maps Jakarta Enterprise Beans annotations to CDI and OmniServices ones. A build compatible extension, so it runs on
 * CDI Lite implementations (such as Quarkus' ArC) and on CDI Full implementations alike.
 *
 * <p>
 * Stateless session beans ({@link Stateless}) become {@link Pooled} beans and singleton session beans ({@link Singleton})
 * application scoped beans with a {@link Lock}.
 *
 * Both are named, run their methods in a transaction as specified by {@link TransactionAttribute} and {@link TransactionManagement},
 * and run the interceptors of {@link jakarta.interceptor.Interceptors} inside the lock and the transaction.
 *
 * Their system exceptions discard a stateless bean's instance and reach the caller as an {@code EJBException}
 * ({@link SystemExceptionInterceptor}), and a transaction that a bean with Bean Managed Transactions leaves open is rolled back
 * ({@link BeanManagedTransactionsInterceptor}).
 *
 * The calls of those that their annotations secure are authorized through Jakarta Authorization
 * ({@link SessionBeanSecurityInterceptor}), and they run as their run-as role ({@link SessionBeanRunAsInterceptor}).
 * The statements of all session beans are put into service at startup ({@link SessionBeanPolicyContextInitializer}).
 *
 * <p>
 * For all classes, {@link EJB} injection points become {@link Inject} ones, as do {@link Resource} ones of a {@link SessionContext} or
 * {@link EJBContext}, and {@link jakarta.ejb.Asynchronous} methods OmniServices asynchronous methods.
 *
 * <p>
 * Not supported: {@code EJB.lookup()}, {@code EJB.mappedName()} and {@code EJB.beanInterface()},
 * {@code ApplicationException.rollback()}, and the interceptors' lifecycle callbacks. On Weld, classes that only have
 * {@link Stateless} or {@link Singleton} are only discovered in bean archives with bean discovery mode {@code all}.
 */
public class OmniBeansExtension implements BuildCompatibleExtension {

    /**
     * The classes of this library that are beans. The library isn't a bean archive, so they're added explicitly.
     */
    private static final Class<?>[] BEAN_CLASSES = {
        BeanManagedTransactionsInterceptor.class,
        RetargetClassInterceptor.class,
        RetargetInterceptor.class,
        SecurityDescriptorRegistry.class,
        SessionBeanRunAsInterceptor.class,
        SessionBeanSecurityInterceptor.class,
        SessionContextImpl.class,
        SystemExceptionInterceptor.class
    };

    /**
     * The superclasses of servlets and filters in the Servlet API, which isn't a bean archive. Quarkus' ArC only calls
     * an {@link Enhancement} method for the subclasses it reaches through the classes in its bean archive index, so
     * without these it skips servlets and filters, and their {@link EJB} injection points aren't mapped.
     */
    private static final String[] SERVLET_SUPERCLASSES = {
        "jakarta.servlet.GenericServlet",
        "jakarta.servlet.http.HttpServlet",
        "jakarta.servlet.GenericFilter",
        "jakarta.servlet.http.HttpFilter"
    };

    // Per binary name of a session bean class, its security
    private final Map<String, SecurityData> securityDataPerBean = new HashMap<>();

    // The roles that the annotations of all classes name
    private final Set<String> applicationRoles = new TreeSet<>();

    /**
     * Creates the extension. Used by CDI.
     */
    public OmniBeansExtension() {
    }

    /**
     * Registers the beans and interceptors of this library, and the superclasses of servlets and filters for the Servlet
     * API.
     *
     * @param scannedClasses to add the classes to
     */
    @Discovery
    public void registerBeans(ScannedClasses scannedClasses) {
        for (Class<?> beanClass : BEAN_CLASSES) {
            scannedClasses.add(beanClass.getName());
        }

        // The qualifier of the synthetic beans, which Quarkus' ArC must find in its index to create them
        scannedClasses.add(SessionBeanClass.class.getName());

        for (String superclass : SERVLET_SUPERCLASSES) {
            if (isAvailable(superclass)) {
                scannedClasses.add(superclass);
            }
        }
    }

    /**
     * Reads the security of a class: the roles its annotations name, and if it's a session bean, who may call its
     * business methods. Before {@link #mapEnterpriseBeans(ClassConfig)}, which removes the annotations from session
     * beans.
     *
     * @param classInfo a class to inspect
     */
    @Enhancement(types = Object.class, withSubtypes = true)
    @Priority(APPLICATION)
    public void collectSessionBeanSecurity(ClassInfo classInfo) {
        applicationRoles.addAll(SecurityData.getRoles(classInfo));

        if (isSessionBean(classInfo)) {
            SecurityData securityData = SecurityData.read(classInfo);
            securityDataPerBean.put(securityData.beanClass(), securityData);
            applicationRoles.addAll(securityData.roles());
        }
    }

    /**
     * Maps the Enterprise Beans annotations of a class.
     *
     * @param classConfig a class to inspect
     */
    @Enhancement(types = Object.class, withSubtypes = true)
    public void mapEnterpriseBeans(ClassConfig classConfig) {
        ClassInfo classInfo = classConfig.info();
        AnnotationInfo stateless = classInfo.annotation(Stateless.class);
        AnnotationInfo singleton = classInfo.annotation(Singleton.class);

        if (stateless != null) {
            mapSessionBean(classConfig, stateless, securityDataPerBean.get(classInfo.name()));

            // A stateless bean that's explicitly @Pooled gets its interceptor from OmniServices
            if (!classInfo.hasAnnotation(Pooled.class)) {
                classConfig.addAnnotation(Pooled.Literal.INSTANCE);
                classConfig.addAnnotation(PooledScopeEnabled.Literal.INSTANCE);
            }
        } else if (singleton != null) {
            mapSessionBean(classConfig, singleton, securityDataPerBean.get(classInfo.name()));

            // Also when it's inherited, as Quarkus' ArC only discovers classes that have a bean defining annotation of
            // their own
            if (!classInfo.hasAnnotation(ApplicationScoped.class) || isInherited(classInfo, ApplicationScoped.class)) {
                classConfig.addAnnotation(ApplicationScoped.Literal.INSTANCE);
            }

            if (!isEnumMember(classInfo.annotation(ConcurrencyManagement.class), "BEAN")) {
                mapLocks(classConfig);
            }
        } else {
            // Also for other classes
            for (MethodConfig method : classConfig.methods()) {
                mapTransactionAttribute(method);
            }
        }

        mapAsynchronous(classConfig);
        mapInjection(classConfig);
    }

    /**
     * Registers the security of the session beans as beans: a {@link SecurityDescriptor} per session bean, and the
     * {@link ApplicationRoles}.
     *
     * <p>
     * This is done so data can cross the build-time / run-time barrier for those environments that actually
     * run this extension during build-time.
     *
     * @param syntheticComponents to register the beans with
     */
    @Synthesis
    public void registerSessionBeanSecurity(SyntheticComponents syntheticComponents) {
        // Store the security data for all session beans as synthetic bean parameters
        for (SecurityData securityData : securityDataPerBean.values()) {
            SyntheticBeanBuilder<SecurityDescriptor> bean =
                syntheticComponents.addBean(SecurityDescriptor.class)
                                   .type(SecurityDescriptor.class)
                                   .qualifier(SessionBeanClass.Literal.of(securityData.beanClass()))
                                   .createWith(SecurityDescriptorCreator.class);

            SecurityDescriptorCreator.withParams(bean, securityData);
        }

        // Store the set of application roles as synthetic bean parameters
        SyntheticBeanBuilder<ApplicationRoles> bean =
            syntheticComponents.addBean(ApplicationRoles.class)
                               .type(ApplicationRoles.class)
                               .createWith(ApplicationRolesCreator.class);

        ApplicationRolesCreator.withParams(bean, applicationRoles);
    }

    private static void mapSessionBean(ClassConfig classConfig, AnnotationInfo sessionBean, SecurityData security) {
        ClassInfo classInfo = classConfig.info();

        if (!classInfo.hasAnnotation(Named.class)) {
            String name = sessionBean.member("name").asString();
            classConfig.addAnnotation(NamedLiteral.of(name.isEmpty() ? classInfo.simpleName() : name));
        }

        classConfig.addAnnotation(SystemExceptions.Literal.INSTANCE);

        mapSecurity(classConfig, security);
        mapTransactions(classConfig);
        mapInterceptors(classConfig);
    }

}
