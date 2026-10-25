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
import java.util.concurrent.Future;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cancels an asynchronous method that started, and responds with what that and the method returned.
 *
 * @serial exclude
 */
@WebServlet("/cancel")
public class CancelServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private CancellableBean cancellableBean;

    /**
     * Creates the servlet.
     */
    public CancelServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringBuilder body = new StringBuilder();
        try {
            Future<String> future = cancellableBean.awaitCancel();
            if (!CancellableBean.STARTED.await(10, SECONDS)) {
                body.append("not started\n");
            }
            body.append("cancel: ").append(future.cancel(true)).append('\n');
            body.append("cancelled: ").append(future.isCancelled()).append('\n');
            body.append("result: ").append(future.get(30, SECONDS)).append('\n');
        } catch (Exception e) {
            body.append("failed: ").append(e).append('\n');
        }

        response.setContentType("text/plain");
        response.getWriter().write(body.toString());
    }
}
