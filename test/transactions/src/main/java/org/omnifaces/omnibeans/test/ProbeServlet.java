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
package org.omnifaces.omnibeans.test;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import javax.naming.InitialContext;

import jakarta.ejb.EJBException;
import jakarta.ejb.EJBTransactionRequiredException;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

/**
 * Runs one probe of the transactions of session beans, and responds with PASS or FAIL, the reasons and the events.
 *
 * @serial exclude
 */
@WebServlet("/probe")
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // jakarta.transaction.Status
    private static final int COMMITTED = 3;
    private static final int ROLLED_BACK = 4;

    @Inject
    private Events events;

    @Inject
    private StatelessBeanManaged statelessBeanManaged;

    @Inject
    private SingletonBeanManaged singletonBeanManaged;

    @Inject
    private StatelessContainerManaged statelessContainerManaged;

    @Inject
    private SingletonContainerManaged singletonContainerManaged;

    @Inject
    private TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    @Inject
    private Instance<UserTransaction> userTransaction;

    /**
     * Creates the servlet.
     */
    public ProbeServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        List<String> failures = new ArrayList<>();
        events.clear();

        try {
            switch (name) {
                case "commit" -> commit(failures);
                case "leak" -> leak(failures);
                case "leakSingleton" -> leakSingleton(failures);
                case "leakAsync" -> leakAsync(failures);
                case "required" -> required(failures);
                case "requiredSingleton" -> requiredSingleton(failures);
                case "requiresNew" -> requiresNew(failures);
                case "mandatory" -> mandatory(failures);
                case "supports" -> supports(failures);
                case "notSupported" -> notSupported(failures);
                case "never" -> never(failures);
                case "asyncInCallerTransaction" -> asyncInCallerTransaction(failures);
                case "beanManagedInCallerTransaction" -> beanManagedInCallerTransaction(failures);
                case "futureFailsUnchecked" -> futureFailsUnchecked(failures);
                case "futureFailsChecked" -> futureFailsChecked(failures);
                case "selfInvocation" -> events.add("self-invocation " + statelessBeanManaged.selfCall());
                default -> failures.add("unknown probe " + name);
            }
        } catch (Throwable throwable) {
            failures.add("unexpected " + throwable);
        }

        StringBuilder body = new StringBuilder(failures.isEmpty() ? "PASS" : "FAIL " + failures).append('\n');
        events.events().forEach(event -> body.append("event: ").append(event).append('\n'));
        response.setContentType("text/plain");
        response.getWriter().write(body.toString());
    }

    /**
     * A transaction the bean completes works, and the instance stays.
     */
    private void commit(List<String> failures) throws Exception {
        String instance = statelessBeanManaged.instance();

        statelessBeanManaged.commit();

        checkCompleted("commit", COMMITTED, failures);
        if (events.events().contains("destroyed " + instance) || !statelessBeanManaged.instance().equals(instance)) {
            failures.add("the instance was discarded");
        }
    }

    /**
     * A transaction the stateless bean leaves open is rolled back, the caller gets an EJBException, the instance is
     * discarded, and neither this thread nor the next call has the transaction.
     */
    private void leak(List<String> failures) throws Exception {
        String instance = statelessBeanManaged.instance();

        try {
            statelessBeanManaged.leak();
            failures.add("no exception");
        } catch (EJBException expected) {
            // Expected
        }

        checkCompleted("leak", ROLLED_BACK, failures);
        if (transactionSynchronizationRegistry.getTransactionKey() != null) {
            failures.add("the caller's thread has the transaction");
        }
        if (!statelessBeanManaged.transactionKey().equals("null")) {
            failures.add("the next call has a transaction");
        }
        if (!events.events().contains("destroyed " + instance)) {
            failures.add("the instance wasn't discarded");
        }
        if (statelessBeanManaged.instance().equals(instance)) {
            failures.add("the discarded instance is still used");
        }
    }

    /**
     * A transaction the singleton bean leaves open is rolled back and the caller gets an EJBException, but the singleton
     * isn't discarded.
     */
    private void leakSingleton(List<String> failures) throws Exception {
        String instance = singletonBeanManaged.instance();

        try {
            singletonBeanManaged.leak();
            failures.add("no exception");
        } catch (EJBException expected) {
            // Expected
        }

        checkCompleted("singleton leak", ROLLED_BACK, failures);
        if (transactionSynchronizationRegistry.getTransactionKey() != null) {
            failures.add("the caller's thread has the transaction");
        }
        if (events.events().contains("destroyed " + instance) || !singletonBeanManaged.instance().equals(instance)) {
            failures.add("the singleton was discarded");
        }
    }

    /**
     * A transaction an asynchronous method leaves open is rolled back on the worker, and the caller's future fails with an
     * EJBException; later asynchronous calls have no transaction.
     */
    private void leakAsync(List<String> failures) throws Exception {
        try {
            statelessBeanManaged.leakAsync().get(30, SECONDS);
            failures.add("no exception");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof EJBException)) {
                failures.add("cause " + e.getCause());
            }
        }

        checkCompleted("async leak", ROLLED_BACK, failures);
        for (int i = 0; i < 10; i++) {
            String key = statelessBeanManaged.transactionKeyAsync().get(30, SECONDS);
            if (!key.equals("null")) {
                failures.add("asynchronous call " + i + " has transaction " + key);
                break;
            }
        }
    }

    /**
     * REQUIRED, the default: without a transaction of the caller, the method runs in a new one, which commits when it
     * returns; with one, it joins it.
     */
    private void required(List<String> failures) throws Exception {
        startsTransaction("required alone", statelessContainerManaged::required, failures);
        joinsTransaction("required joined", statelessContainerManaged::required, failures);
    }

    /**
     * The same for a singleton bean.
     */
    private void requiredSingleton(List<String> failures) throws Exception {
        startsTransaction("singleton alone", singletonContainerManaged::required, failures);
        joinsTransaction("singleton joined", singletonContainerManaged::required, failures);
    }

    /**
     * REQUIRES_NEW on a method of a bean with the default: the method runs in a transaction of its own, which commits
     * when it returns, and the caller's transaction is the thread's again afterwards.
     */
    private void requiresNew(List<String> failures) throws Exception {
        inCallerTransaction(callerKey -> {
            String key = statelessContainerManaged.requiresNew("requires new");
            if (key.equals("null") || key.equals(callerKey)) {
                failures.add("ran in transaction " + key + ", the caller's is " + callerKey);
            }
            checkCompleted("requires new", COMMITTED, failures);
            checkCallerTransaction(callerKey, failures);
        });
    }

    /**
     * MANDATORY: without a transaction of the caller, the caller gets an EJBTransactionRequiredException, and the
     * instance isn't discarded; with one, the method joins it.
     */
    private void mandatory(List<String> failures) throws Exception {
        String instance = statelessContainerManaged.instance();
        try {
            statelessContainerManaged.mandatory("mandatory alone");
            failures.add("no exception without a transaction");
        } catch (EJBTransactionRequiredException expected) {
            // Expected
        } catch (EJBException e) {
            failures.add("without a transaction: " + e + ", caused by " + e.getCause());
        }
        checkNotDiscarded(instance, failures);

        joinsTransaction("mandatory joined", statelessContainerManaged::mandatory, failures);
    }

    /**
     * SUPPORTS: without a transaction of the caller, the method runs without one; with one, it joins it.
     */
    private void supports(List<String> failures) throws Exception {
        String key = statelessContainerManaged.supports("supports alone");
        if (!key.equals("null")) {
            failures.add("ran in transaction " + key + " without the caller's");
        }

        joinsTransaction("supports joined", statelessContainerManaged::supports, failures);
    }

    /**
     * NOT_SUPPORTED: the method runs without a transaction, also when the caller has one, which is the thread's again
     * afterwards.
     */
    private void notSupported(List<String> failures) throws Exception {
        inCallerTransaction(callerKey -> {
            String key = statelessContainerManaged.notSupported("not supported");
            if (!key.equals("null")) {
                failures.add("ran in transaction " + key + ", the caller's is " + callerKey);
            }
            checkCallerTransaction(callerKey, failures);
        });
    }

    /**
     * NEVER: without a transaction of the caller, the method runs without one; with one, the caller gets an
     * EJBException, and the instance isn't discarded.
     */
    private void never(List<String> failures) throws Exception {
        String key = statelessContainerManaged.never("never alone");
        if (!key.equals("null")) {
            failures.add("ran in transaction " + key + " without the caller's");
        }

        String instance = statelessContainerManaged.instance();
        inCallerTransaction(callerKey -> {
            try {
                statelessContainerManaged.never("never joined");
                failures.add("no exception with the caller's transaction");
            } catch (EJBException expected) {
                if (expected.getClass() != EJBException.class) {
                    failures.add("with the caller's transaction: " + expected);
                }
            }
            checkCallerTransaction(callerKey, failures);
        });
        checkNotDiscarded(instance, failures);
    }

    /**
     * An asynchronous method with the default transaction attribute, called with a transaction of the caller, runs in a
     * transaction of its own, which has committed when the caller's future completes.
     */
    private void asyncInCallerTransaction(List<String> failures) throws Exception {
        inCallerTransaction(callerKey -> {
            String key = statelessContainerManaged.requiredAsync("async").get(30, SECONDS);
            if (key.equals("null") || key.equals(callerKey)) {
                failures.add("ran in transaction " + key + ", the caller's is " + callerKey);
            }
            checkCompleted("async", COMMITTED, failures);
            checkCallerTransaction(callerKey, failures);
        });
    }

    /**
     * A bean with Bean Managed Transactions, called with a transaction of the caller, starts without a transaction, its
     * own transaction works, and the caller's transaction is the thread's again afterwards.
     */
    private void beanManagedInCallerTransaction(List<String> failures) throws Exception {
        inCallerTransaction(callerKey -> {
            String result = statelessBeanManaged.ownTransaction();
            if (!result.startsWith("before=null own=") || result.equals("before=null own=null") || result.endsWith("own=" + callerKey)) {
                failures.add("transactions " + result + ", the caller's is " + callerKey);
            }
            checkCompleted("own", COMMITTED, failures);
            checkCallerTransaction(callerKey, failures);
        });
    }

    /**
     * A future returned by an asynchronous method that fails with an unchecked exception: the method's transaction rolls
     * back, and the caller's future fails with an EJBException (a system exception) caused by it.
     */
    private void futureFailsUnchecked(List<String> failures) throws Exception {
        try {
            statelessContainerManaged.failLater("fails unchecked", false).get(30, SECONDS);
            failures.add("no exception");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof EJBException ejbException && ejbException.getCause() instanceof IllegalStateException)) {
                failures.add("cause " + e.getCause() + ", caused by " + (e.getCause() == null ? null : e.getCause().getCause()));
            }
        }
        checkCompleted("fails unchecked", ROLLED_BACK, failures);
    }

    /**
     * A future returned by an asynchronous method that fails with a checked exception: the method's transaction commits,
     * and the caller's future fails with that exception (an application exception).
     */
    private void futureFailsChecked(List<String> failures) throws Exception {
        try {
            statelessContainerManaged.failLater("fails checked", true).get(30, SECONDS);
            failures.add("no exception");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof FutureProblem)) {
                failures.add("cause " + e.getCause());
            }
        }
        checkCompleted("fails checked", COMMITTED, failures);
    }

    /**
     * Without a transaction of the caller, the method runs in a new one, which commits when it returns.
     */
    private void startsTransaction(String label, Function<String, String> method, List<String> failures) {
        String key = method.apply(label);
        if (key.equals("null")) {
            failures.add(label + ": no transaction");
        }
        checkCompleted(label, COMMITTED, failures);
    }

    /**
     * With a transaction of the caller, the method joins it: it runs in it, and completes with it.
     */
    private void joinsTransaction(String label, Function<String, String> method, List<String> failures) throws Exception {
        inCallerTransaction(callerKey -> {
            String key = method.apply(label);
            if (!key.equals(callerKey)) {
                failures.add(label + ": ran in transaction " + key + ", the caller's is " + callerKey);
            }
            if (events.events().stream().anyMatch(event -> event.startsWith(label + " completed"))) {
                failures.add(label + ": completed before the caller's transaction");
            }
        });
        checkCompleted(label, COMMITTED, failures);
    }

    /**
     * Runs the given code in a transaction of the caller, which is committed afterwards, or rolled back when it was
     * marked so.
     */
    private void inCallerTransaction(CallerCode code) throws Exception {
        UserTransaction transaction = userTransaction.isResolvable() ? userTransaction.get() : (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        transaction.begin();
        try {
            code.run(currentTransactionKey());
        } finally {
            if (transaction.getStatus() == Status.STATUS_MARKED_ROLLBACK) {
                transaction.rollback();
            } else {
                transaction.commit();
            }
        }
    }

    /**
     * Code that runs in a transaction of the caller.
     */
    @FunctionalInterface
    private interface CallerCode {
        void run(String callerKey) throws Exception;
    }

    private String currentTransactionKey() {
        return String.valueOf(transactionSynchronizationRegistry.getTransactionKey());
    }

    private void checkCallerTransaction(String callerKey, List<String> failures) {
        if (!currentTransactionKey().equals(callerKey)) {
            failures.add("the thread has transaction " + currentTransactionKey() + " afterwards, not the caller's " + callerKey);
        }
    }

    private void checkNotDiscarded(String instance, List<String> failures) {
        if (events.events().contains("destroyed " + instance) || !statelessContainerManaged.instance().equals(instance)) {
            failures.add("the instance was discarded");
        }
    }

    private void checkCompleted(String label, int status, List<String> failures) {
        if (!events.events().contains(label + " completed status=" + status)) {
            failures.add("no '" + label + " completed status=" + status + "'");
        }
    }
}
