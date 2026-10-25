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

import jakarta.ejb.EJBException;
import jakarta.ejb.EJBTransactionRolledbackException;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs one probe of how session beans handle exceptions, and responds with PASS or FAIL, the reasons and the events.
 *
 * @serial exclude
 */
@WebServlet("/probe")
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private Events events;

    @Inject
    private StatelessThrower statelessThrower;

    @Inject
    private SingletonThrower singletonThrower;

    @Inject
    private TransactionalCaller transactionalCaller;

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
                case "systemException" -> systemException(() -> statelessThrower.systemException(), IllegalStateException.class, failures);
                case "error" -> systemException(() -> statelessThrower.error(), AssertionError.class, failures);
                case "notInheritingSystemException" -> systemException(() -> statelessThrower.notInheritingSystemException(), NotInheritingSystemProblem.class, failures);
                case "ejbException" -> ejbException(failures);
                case "applicationException" -> applicationException(() -> statelessThrower.applicationException(), ApplicationProblem.class, failures);
                case "applicationRuntimeException" -> applicationException(() -> statelessThrower.applicationRuntimeException(), ApplicationRuntimeProblem.class, failures);
                case "inheritedApplicationRuntimeException" -> applicationException(() -> statelessThrower.inheritedApplicationRuntimeException(), InheritedApplicationRuntimeProblem.class, failures);
                case "singletonSystemException" -> singletonSystemException(failures);
                case "callerTransaction" -> callerTransaction(failures);
                case "asyncSystemException" -> asyncSystemException(failures);
                case "asyncApplicationException" -> asyncApplicationException(failures);
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
     * A system exception reaches the caller as an EJBException with the exception as cause, and discards the instance.
     */
    private void systemException(Call call, Class<? extends Throwable> causeType, List<String> failures) throws Exception {
        String instance = statelessThrower.instance();

        try {
            call.run();
            failures.add("no exception");
        } catch (EJBException e) {
            if (e.getClass() != EJBException.class) {
                failures.add("not an EJBException but " + e.getClass().getName());
            }
            if (!causeType.isInstance(e.getCause())) {
                failures.add("cause " + e.getCause() + " isn't a " + causeType.getSimpleName());
            }
        }

        checkDiscarded(instance, failures);
    }

    /**
     * An EJBException the bean throws itself reaches the caller as is, and discards the instance.
     */
    private void ejbException(List<String> failures) {
        String instance = statelessThrower.instance();

        try {
            statelessThrower.ejbException();
            failures.add("no exception");
        } catch (EJBException e) {
            if (!"bean".equals(e.getMessage())) {
                failures.add("wrapped: " + e);
            }
        }

        checkDiscarded(instance, failures);
    }

    /**
     * An application exception reaches the caller as is, and the instance stays.
     */
    private void applicationException(Call call, Class<? extends Exception> type, List<String> failures) {
        String instance = statelessThrower.instance();

        try {
            call.run();
            failures.add("no exception");
        } catch (Exception e) {
            if (e.getClass() != type) {
                failures.add("not a " + type.getSimpleName() + " but " + e);
            }
        }

        if (events.events().contains("destroyed " + instance)) {
            failures.add("the instance was discarded");
        }
        if (!statelessThrower.instance().equals(instance)) {
            failures.add("the instance was replaced");
        }
    }

    /**
     * A singleton isn't discarded after a system exception.
     */
    private void singletonSystemException(List<String> failures) {
        String instance = singletonThrower.instance();

        try {
            singletonThrower.systemException();
            failures.add("no exception");
        } catch (EJBException e) {
            if (!(e.getCause() instanceof IllegalStateException)) {
                failures.add("cause " + e.getCause());
            }
        }

        if (events.events().contains("destroyed " + instance) || !singletonThrower.instance().equals(instance)) {
            failures.add("the singleton was discarded");
        }
    }

    /**
     * A system exception in the caller's transaction reaches the caller as an EJBTransactionRolledbackException, and the
     * transaction is marked for rollback.
     */
    private void callerTransaction(List<String> failures) {
        String outcome = transactionalCaller.callSystemException();

        if (!outcome.equals(EJBTransactionRolledbackException.class.getSimpleName() + " rollbackOnly=true")) {
            failures.add("outcome " + outcome);
        }
    }

    /**
     * An asynchronous method's system exception is the cause of an EJBException, the cause of the ExecutionException.
     */
    private void asyncSystemException(List<String> failures) throws Exception {
        try {
            statelessThrower.asyncSystemException().get(30, SECONDS);
            failures.add("no exception");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof EJBException ejbException) || !(ejbException.getCause() instanceof IllegalStateException)) {
                failures.add("cause " + e.getCause() + (e.getCause() == null ? "" : ", its cause " + e.getCause().getCause()));
            }
        }
    }

    /**
     * An asynchronous method's application exception is the cause of the ExecutionException.
     */
    private void asyncApplicationException(List<String> failures) throws Exception {
        try {
            statelessThrower.asyncApplicationException().get(30, SECONDS);
            failures.add("no exception");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof ApplicationProblem)) {
                failures.add("cause " + e.getCause());
            }
        }
    }

    private void checkDiscarded(String instance, List<String> failures) {
        if (!events.events().contains("destroyed " + instance)) {
            failures.add("the instance wasn't discarded");
        }
        if (statelessThrower.instance().equals(instance)) {
            failures.add("the discarded instance is still used");
        }
    }

    @FunctionalInterface
    private interface Call {
        void run() throws Exception;
    }
}
