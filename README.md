# omnibeans

OmniBeans intends to implement Enterprise Beans Lite using existing Jakarta EE APIs, mainly:

* Jakarta CDI
* Jakarta Interceptors
* Jakarta Transactions
* Jakarta Concurrency

As an initial example, the following bean works on Piranha Micro A (which doens't implement Enterprise Beans)

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