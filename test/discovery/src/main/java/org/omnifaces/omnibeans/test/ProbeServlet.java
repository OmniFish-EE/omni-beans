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
import java.util.Set;
import java.util.function.Function;

import org.omnifaces.omnibeans.test.lib.LibSingleton;
import org.omnifaces.omnibeans.test.lib.LibStateless;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responds with the scope of each session bean class, and what it returns when it's called.
 *
 * @serial exclude
 */
@WebServlet("/probe")
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private BeanManager beanManager;

    /**
     * Creates the servlet.
     */
    public ProbeServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String body = describe(WarStateless.class, WarStateless::hello)
                + describe(WarSingleton.class, WarSingleton::hello)
                + describe(LibStateless.class, LibStateless::hello)
                + describe(LibSingleton.class, LibSingleton::hello);

        response.setContentType("text/plain");
        response.getWriter().write(body);
    }

    private <T> String describe(Class<T> type, Function<T, String> hello) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        if (beans.isEmpty()) {
            return type.getSimpleName() + ": not a bean\n";
        }

        Bean<?> bean = beanManager.resolve(beans);
        String result;
        try {
            result = hello.apply(type.cast(beanManager.getReference(bean, type, beanManager.createCreationalContext(bean))));
        } catch (RuntimeException e) {
            result = "failed: " + e;
        }

        return type.getSimpleName() + ": scope=" + bean.getScope().getSimpleName() + " hello=" + result + "\n";
    }
}
