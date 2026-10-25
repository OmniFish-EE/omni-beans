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

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responds with what the beans with {@code @EJB} injection points return, and with what its own {@code @EJB} field
 * (whose superclass is in the Servlet API, a library that isn't a bean archive) refers to.
 *
 * @serial exclude
 */
@WebServlet("/probe")
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private GreetingFacade greetingFacade;

    @Inject
    private GreetingHolder greetingHolder;

    @Inject
    private SelfInjected selfInjected;

    @EJB(beanName = "english")
    private Greeter english;

    /**
     * Creates the servlet.
     */
    public ProbeServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String body;
        try {
            body = "facade: " + greetingFacade.greetings() + "\nholder: " + greetingHolder.greet() + "\nself: " + selfInjected.outer()
                + "\ncontexts: " + greetingFacade.contexts() + "\nservlet: " + (english == null ? "not injected" : english.greet());
        } catch (RuntimeException e) {
            body = "failed: " + e;
        }

        response.setContentType("text/plain");
        response.getWriter().write(body);
    }
}
