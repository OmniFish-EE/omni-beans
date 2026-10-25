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

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs one probe of the interceptors of session beans ({@code @Interceptors}), and responds with PASS or FAIL, the
 * reasons and the events.
 *
 * @serial exclude
 */
@WebServlet("/probe")
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private InterceptedStateless interceptedStateless;

    @Inject
    private InterceptedSingleton interceptedSingleton;

    /**
     * Creates the servlet.
     */
    public ProbeServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        List<String> failures = new ArrayList<>();
        List<String> events = new ArrayList<>();

        try {
            switch (name) {
                case "chain" -> check(interceptedStateless.call("in"), "method got in>First>Base>Second>Method", events, failures,
                        "First chain=[]", "Base chain=[First]", "Second chain=[First, Base]", "Method chain=[First, Base, Second]", "method");
                case "chainSingleton" -> check(interceptedSingleton.call("in"), "method got in>First>Method", events, failures,
                        "First chain=[]", "Method chain=[First]", "method");
                case "excludeClassInterceptors" -> {
                    check(interceptedStateless.excluded("in"), "method got in>Method", events, failures, "Method chain=[]", "method");
                    check(interceptedStateless.excludedOnly("in"), "method got in", events, failures, "method");
                }
                case "shortCircuit" -> shortCircuit(events, failures);
                default -> failures.add("unknown probe " + name);
            }
        } catch (Throwable throwable) {
            failures.add("unexpected " + throwable);
        }

        StringBuilder body = new StringBuilder(failures.isEmpty() ? "PASS" : "FAIL " + failures).append('\n');
        events.forEach(event -> body.append("event: ").append(event).append('\n'));
        response.setContentType("text/plain");
        response.getWriter().write(body.toString());
    }

    /**
     * A short-circuiting interceptor, and a call that isn't intercepted, return the pool's one instance: the calls after
     * them don't time out.
     */
    private void shortCircuit(List<String> events, List<String> failures) {
        Trace.clear();
        interceptedStateless.toString();
        events.addAll(Trace.events());

        for (int i = 0; i < 2; i++) {
            check(interceptedStateless.shortCircuit("in"), "short-circuited", events, failures,
                    "First chain=[]", "Base chain=[First]", "Second chain=[First, Base]", "ShortCircuit");
        }

        check(interceptedStateless.call("in"), "method got in>First>Base>Second>Method", events, failures,
                "First chain=[]", "Base chain=[First]", "Second chain=[First, Base]", "Method chain=[First, Base, Second]", "method");
    }

    /**
     * Checks the result of a call, and that the interceptors and the method ran once each, in the expected order, on
     * one instance. Clears the trace for the next call.
     */
    private static void check(String result, String expectedResult, List<String> events, List<String> failures, String... expectedEvents) {
        List<String> trace = Trace.events();
        Trace.clear();
        events.addAll(trace);

        if (!result.equals(expectedResult)) {
            failures.add("result " + result + ", expected " + expectedResult);
        }

        List<String> withoutTargets = trace.stream().map(event -> event.replaceAll(" target=\\S+$", "")).toList();
        if (!withoutTargets.equals(List.of(expectedEvents))) {
            failures.add("events " + withoutTargets + ", expected " + List.of(expectedEvents));
        }

        if (trace.stream().map(event -> event.replaceAll(".* target=", "")).distinct().count() > 1) {
            failures.add("not all on one instance: " + trace);
        }
    }
}
