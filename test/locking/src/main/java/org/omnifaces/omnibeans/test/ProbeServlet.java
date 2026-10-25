/*
 * Copyright 2026 OmniFish
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import jakarta.ejb.ConcurrentAccessException;
import jakarta.ejb.ConcurrentAccessTimeoutException;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs one probe of the locks of singleton beans while another thread holds one, and responds with PASS or FAIL and the
 * reasons.
 *
 * @serial exclude
 */
@WebServlet("/probe")
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private LockedSingleton lockedSingleton;

    @Inject
    private BeanManagedSingleton beanManagedSingleton;

    @Inject
    private Latches latches;

    @Inject
    private Instance<RequestContextController> requestContextControllers;

    /**
     * Creates the servlet.
     */
    public ProbeServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        try {
            switch (name) {
                case "readersShare" -> whileHeld(name, () -> lockedSingleton.read(name),
                        () -> succeeds(lockedSingleton::quickRead, notes, failures), failures);
                case "writerWaitsForReaders" -> whileHeld(name, () -> lockedSingleton.read(name),
                        () -> timesOut(lockedSingleton::quickWrite, notes, failures), failures);
                case "readerWaitsForWriter" -> whileHeld(name, () -> lockedSingleton.write(name),
                        () -> timesOut(lockedSingleton::quickRead, notes, failures), failures);
                case "notPermitted" -> whileHeld(name, () -> lockedSingleton.read(name),
                        () -> notPermitted(lockedSingleton::writeNow, notes, failures), failures);
                case "beanManagedConcurrency" -> whileHeld(name, () -> beanManagedSingleton.hold(name),
                        () -> succeeds(beanManagedSingleton::quick, notes, failures), failures);
                case "loopback" -> loopback(notes, failures);
                default -> failures.add("unknown probe " + name);
            }
        } catch (Throwable throwable) {
            failures.add("unexpected " + throwable);
        }

        StringBuilder body = new StringBuilder(failures.isEmpty() ? "PASS" : "FAIL " + failures).append('\n');
        notes.forEach(note -> body.append("note: ").append(note).append('\n'));
        response.setContentType("text/plain");
        response.getWriter().write(body.toString());
    }

    /**
     * Runs the check while another thread is in the given call, which holds its lock until released.
     */
    private void whileHeld(String name, Runnable holder, Runnable check, List<String> failures) throws InterruptedException {
        Thread holderThread = newThread(holder, "holder");
        holderThread.start();
        try {
            if (!latches.awaitEntered(name, 10_000)) {
                failures.add("the holder didn't enter");
                return;
            }
            check.run();
        } finally {
            latches.release(name);
            holderThread.join(10_000);
        }
    }

    /**
     * The call succeeds at once.
     */
    private static void succeeds(Callable<String> call, List<String> notes, List<String> failures) {
        long start = System.nanoTime();
        try {
            notes.add("result " + call.call());
        } catch (Exception e) {
            failures.add("failed: " + e);
        }
        long millis = millisSince(start);
        notes.add("took " + millis + " ms");
        if (millis > 400) {
            failures.add("waited " + millis + " ms");
        }
    }

    /**
     * The call fails with a ConcurrentAccessTimeoutException after the access timeout of 500 ms.
     */
    private static void timesOut(Callable<String> call, List<String> notes, List<String> failures) {
        long start = System.nanoTime();
        try {
            failures.add("succeeded: " + call.call());
        } catch (ConcurrentAccessTimeoutException expected) {
            long millis = millisSince(start);
            notes.add("timed out after " + millis + " ms");
            if (millis < 400 || millis > 5000) {
                failures.add("timed out after " + millis + " ms, not the access timeout of 500 ms");
            }
        } catch (Exception e) {
            failures.add("failed with " + e + ", caused by " + e.getCause());
        }
    }

    /**
     * The call fails with a ConcurrentAccessException at once.
     */
    private static void notPermitted(Callable<String> call, List<String> notes, List<String> failures) {
        long start = System.nanoTime();
        try {
            failures.add("succeeded: " + call.call());
        } catch (ConcurrentAccessException expected) {
            long millis = millisSince(start);
            notes.add("refused after " + millis + " ms: " + expected);
            if (expected instanceof ConcurrentAccessTimeoutException) {
                failures.add("a timeout, not a refusal: " + expected);
            }
            if (millis > 400) {
                failures.add("refused after " + millis + " ms");
            }
        } catch (Exception e) {
            failures.add("failed with " + e + ", caused by " + e.getCause());
        }
    }

    /**
     * A call that holds the read lock and reaches a method with a write lock again fails at once with an
     * IllegalLoopbackException; the other calls back run under the lock that the call holds.
     */
    private void loopback(List<String> notes, List<String> failures) {
        long start = System.nanoTime();
        String result = lockedSingleton.readThenWrite();
        long millis = millisSince(start);
        notes.add("read, then write: " + result + " after " + millis + " ms");
        if (!result.equals("refused") || millis > 400) {
            failures.add("read, then write: " + result + " after " + millis + " ms");
        }

        loopback("read, then read", lockedSingleton::readThenRead, "read", failures);
        loopback("write, then write", lockedSingleton::writeThenWrite, "written", failures);
        loopback("write, then read", lockedSingleton::writeThenRead, "read", failures);
    }

    private static void loopback(String name, Callable<String> call, String expected, List<String> failures) {
        long start = System.nanoTime();
        try {
            String result = call.call();
            if (!result.equals(expected) || millisSince(start) > 400) {
                failures.add(name + ": " + result + " after " + millisSince(start) + " ms");
            }
        } catch (Exception e) {
            failures.add(name + ": " + e);
        }
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * A thread with a request context, which some @Transactional implementations need (Piranha's Transact).
     */
    private Thread newThread(Runnable runnable, String name) {
        return new Thread(() -> {
            RequestContextController requestContextController = requestContextControllers.get();
            requestContextController.activate();
            try {
                runnable.run();
            } finally {
                requestContextController.deactivate();
            }
        }, name);
    }
}
