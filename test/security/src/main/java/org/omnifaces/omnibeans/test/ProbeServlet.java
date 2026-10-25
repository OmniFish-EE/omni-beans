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

import static org.omnifaces.omnibeans.test.Calls.call;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responds with what the secured beans return to the caller, a line per call. At {@code /secured/probe} the caller is
 * authenticated, at {@code /probe} not necessarily.
 *
 * @serial exclude
 */
@WebServlet({ "/probe", "/secured/probe" })
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private SecuredBean securedBean;

    @Inject
    private Echo<String> echo;

    @Inject
    private RunAsBean runAsBean;

    @Inject
    private GuardedService guardedService;

    @Inject
    private GuardedEjb guardedEjb;

    @Inject
    private AsyncSecuredBean asyncSecuredBean;

    @Inject
    private StartupResults startupResults;

    /**
     * Creates the servlet.
     */
    public ProbeServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String body = "startup: " + startupResults.get()
            + "\nwhoAmI: " + call(securedBean::whoAmI)
            + "\nopen: " + call(securedBean::open)
            + "\nmanagers: " + call(securedBean::managers)
            + "\nnobody: " + call(securedBean::nobody)
            + "\noverloadedInt: " + call(() -> securedBean.overloaded(1))
            + "\noverloadedString: " + call(() -> securedBean.overloaded("1"))
            + "\necho: " + call(() -> echo.echo("echoed"))
            + "\nchain: " + call(runAsBean::chain)
            + "\nfailing: " + call(() -> { runAsBean.failing(); return "not failed"; })
            + "\nrestored: " + call(securedBean::whoAmI)
            + "\nguardedService: " + call(guardedService::guarded)
            + "\nguardedEjb: " + call(guardedEjb::guarded)
            + "\nasync: " + call(() -> asyncSecuredBean.work().get())
            + "\n";

        response.setContentType("text/plain");
        response.getWriter().write(body);
    }
}
