# omnibeans

OmniBeans intends to implement Enterprise Beans Lite using existing Jakarta EE APIs, mainly:

* Jakarta CDI
* Jakarta Interceptors
* Jakarta Transactions
* Jakarta Concurrency

The examples at the end show the kind of beans OmniBeans supports.

## Requirements

Java 17, CDI 4.1 (Jakarta EE 11) and OmniServices 0.5. OmniBeans registers itself with a CDI Lite build compatible
extension, so it runs on CDI Full implementations (such as Weld) as well as on CDI Lite ones (such as Quarkus' ArC).

* The Jakarta Authorization API (3.0), and Exousia 3.0.2 or later as its implementation (as in GlassFish, Piranha, and
  Quarkus with Exousia's Quarkus extension). OmniBeans registers the policy context handlers of the session beans with
  Exousia's `DefaultPolicyContextHandler.register`, which older versions don't have.
* **Quarkus**: add `jakarta.ejb:jakarta.ejb-api`, `jakarta.authorization:jakarta.authorization-api` (or Exousia's
  Quarkus extension) and `io.quarkus:quarkus-narayana-jta`, and for asynchronous methods to run on Quarkus' threads
  `io.quarkus:quarkus-smallrye-context-propagation`. Quarkus rejects `@jakarta.ejb.Singleton` unless
  `quarkus.arc.detect-wrong-annotations=false`.
* **Weld**: a class that only has `@Stateless` or `@Singleton` is only discovered in a bean archive with
  `bean-discovery-mode="all"` (Weld doesn't use the bean defining annotations extensions add for discovery).

## How the annotations are mapped

| Enterprise Beans | Becomes |
|---|---|
| `@Stateless` | an OmniServices `@Pooled` bean |
| `@Singleton` | an `@ApplicationScoped` bean with an OmniServices `@Lock` (none with `@ConcurrencyManagement(BEAN)`) |
| `@TransactionAttribute` | `@Transactional` (default `REQUIRED`) |
| `@TransactionManagement(BEAN)` | `@Transactional(NOT_SUPPORTED)`; use `SessionContext.getUserTransaction()` |
| `@Interceptors` | interceptors that run after the lock, inside the transaction |
| `@EJB` | `@Inject`, with `@Named(beanName)` |
| `@Resource` of a `SessionContext` | `@Inject` |
| `@Asynchronous` | an OmniServices asynchronous method |

Session beans are named after their `name`, else their class' simple name.

### Exceptions

* Application exceptions (checked, or `@ApplicationException`) reach the caller as they are.
* System exceptions are logged and reach the caller as an `EJBException`, or an `EJBTransactionRolledbackException`
  in the caller's transaction. A stateless bean's instance is discarded.
* A lock that isn't acquired in time gives a `ConcurrentAccessTimeoutException`.
* A bean managed transaction left open is rolled back, and the caller gets an `EJBException`.

### Security

Through Jakarta Authorization, as in an Enterprise Beans container:

* At build time, `@RolesAllowed`, `@PermitAll` and `@DenyAll` become `EJBMethodPermission`s, and the application's
  roles `EJBRoleRefPermission`s.
* These go into a policy context of their own (the web module's one plus `_internal`, as in GlassFish), which is put
  into service when the web module starts, before its listeners run.
* A refused call gets an `EJBAccessException`.
* `@RunAs` runs the bean as its role. On Quarkus, add `ee.omnifish:omnibeans-quarkus`.
* `getCallerPrincipal()` and `isCallerInRole()` use the container's caller and policy.
* On Quarkus, the policy replaces Quarkus' own role checks for session beans.

### Quarkus

ArC only maps classes it finds in its bean archive index, so it misses classes whose superclass is in a library that
isn't a bean archive. OmniBeans adds the Servlet API's superclasses, so servlets and filters are covered.

## Not supported

* On Quarkus, plain calls on `this`: ArC intercepts a bean's calls of its own methods, while Enterprise Beans (and Weld)
  don't. So there, a method-level transaction attribute applies to them, and in a bean with Bean Managed Transactions,
  they run with `@Transactional(NOT_SUPPORTED)`, which suspends the bean's own transaction. Use private methods for such
  calls.
* `@EJB` `lookup`, `mappedName` and `beanInterface`.
* `@ApplicationException(rollback = …)`: transactions end as `@Transactional` specifies (unchecked exceptions roll
  back, checked ones don't).
* The lifecycle callbacks and injection of `@Interceptors` interceptors.
* Timers, remote and stateful beans.
* Security: `security-role-ref` links (role references only use the application's role names), and role-protected
  asynchronous methods (their worker has no policy context).

## Building and testing

`mvn install` builds OmniBeans and tests it on Quarkus. The tests load OmniBeans and OmniServices from the local Maven
repository, so use `install`, not `verify`. To test on Piranha Micro instead, use `mvn install -Ppiranha`; for now,
this needs a local Piranha build with the Weld startup fix that isn't upstream yet. The `security` module runs on
Quarkus only, and needs Exousia's Quarkus extension (`org.glassfish.exousia:quarkus-exousia`, 3.0.2 or later).

### Jakarta Enterprise Beans TCK

`tck` runs the Enterprise Beans Lite tests of the Jakarta Enterprise Beans TCK (11.0.3) against OmniBeans: the tests
the TCK tags `web` (required in the Web Profile). For now only in the servlet vehicle; `-Dtck.skipOtherVehicles=false`
adds the other web vehicles (servlet filter, JSP and Faces). It's not part of the regular build, as a full run builds
hundreds of applications:

```
mvn install -f tck              # Quarkus
mvn install -f tck -Ppiranha    # Piranha Micro, in process (needs the local Piranha build, see above)
```

`tck/install` downloads the TCK and installs its jars; `mvn install -f tck/run` skips that. The vehicles are called on
port 8080 (`webServerPort` in `ts.jte`). On Quarkus, each test archive is built into a Quarkus application with
OmniBeans; the JSP and servlet filter vehicles also get WaSP, the Faces vehicle MyFaces.


## Async

```java
import jakarta.ejb.AsyncResult;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;

@Stateless
public class AsyncBean {
    private static final Logger LOGGER = Logger.getLogger(AsyncBean.class.getName());

    @Asynchronous
    public Future<Integer> multiply(int number1, int number2) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Thread.interrupted();
            LOGGER.log(SEVERE, null, ex);
        }

        return new AsyncResult<>(number1 * number2);
    }

}
```

## Singleton


```java
import jakarta.ejb.Singleton;
import jakarta.annotation.PostConstruct;

@Singleton
public class SingletonBean {
    private StringBuilder builder;

    @PostConstruct
    private void postConstruct() {
        builder = new StringBuilder();
    }
    
    public void addValue(String value) {
        builder.append(value);
    }
    
    public String getAccumulatedValues() {
        return builder.toString();
    }
}
```

## Lock

```java
import jakarta.ejb.Lock;
import jakarta.ejb.Singleton;
import jakarta.annotation.PostConstruct;

@Singleton
@Lock(READ)
public class SingletonBean {
    private StringBuilder builder;

    @PostConstruct
    private void postConstruct() {
        builder = new StringBuilder();
    }
    
    @Lock(WRITE)
    public void addValue(String value) {
        builder.append(value);
    }
    
    public String getAccumulatedValues() {
        return builder.toString();
    }
}
```

## Lock and EJB-style Interceptors

```java
import jakarta.ejb.Lock;
import jakarta.ejb.Singleton;
import jakarta.annotation.PostConstruct;
import jakarta.interceptor.Interceptors;

@Singleton
@Lock(READ)
@Interceptors(InterceptorA.class)
public class SingletonBean {
    private StringBuilder builder;

    @PostConstruct
    private void postConstruct() {
        builder = new StringBuilder();
    }
    
    @Lock(WRITE)
    @Interceptors(InterceptorB.class)
    public void addValue(String value) {
        builder.append(value);
    }
    
    public String getAccumulatedValues() {
        return builder.toString();
    }
}
```

## EJB method injection by bean name

```java
import jakarta.ejb.Stateless;
import jakarta.ejb.EJB;

@Stateless
public class InjectedBean {

    SomeInterface injectedBean;

    @EJB(beanName = "SomeBean")
    public void setSomeBean(SomeInterface bean) {
      this.injectedBean = bean;
    }

}
```


## Transaction

```java
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class MyEntityService {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(MyEntity entity ) {
        // Transaction is automatically active here
        entityManager.persist(entity);
    }

    public MyEntity load(int id) {
        return entityManager.find(MyEntity.class, id);
    }
}
```

## Security

```java
import jakarta.ejb.Stateless;
import jakarta.ejb.SessionContext;

import jakarta.annotation.Resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;

@Stateless
public class SecuredBean {

    @Resource
    private SessionContext sessionContext;

    @RolesAllowed("manager")
    public String managers() {
        return "managers";
    }

    @DenyAll
    public String nobody() {
        return "nobody";
    }

    @PermitAll
    public String whoAmI() {
        return 
            sessionContext.getCallerPrincipal().getName() + "/" + 
            sessionContext.isCallerInRole("manager");
    }
}
```




