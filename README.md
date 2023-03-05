# omnibeans

OmniBeans intends to implement Enterprise Beans Lite using existing Jakarta EE APIs, mainly:

* Jakarta CDI
* Jakarta Interceptors
* Jakarta Transactions
* Jakarta Concurrency

As an initial example, the following beans work to some degree on Piranha Micro A (which doens't implement Enterprise Beans)


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




