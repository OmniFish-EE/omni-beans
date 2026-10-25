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

import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.htmlunit.TextPage;
import org.htmlunit.WebClient;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests the interceptors of session beans ({@code @Interceptors}): those of the class run before those of the method,
 * each once, in order, on the instance the method runs on; changed parameters and context data reach the next one;
 * {@code @ExcludeClassInterceptors} excludes those of the class; and an interceptor that doesn't proceed returns the
 * pooled instance.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class InterceptorsTest {

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return create(WebArchive.class)
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "beans.xml"))
                .addClasses(
                    BaseInterceptor.class,
                    FirstInterceptor.class,
                    InterceptedSingleton.class,
                    InterceptedStateless.class,
                    Interception.class,
                    MethodInterceptor.class,
                    ProbeServlet.class,
                    SecondInterceptor.class,
                    ShortCircuitInterceptor.class,
                    Trace.class);
    }

    @Test
    void chain() throws IOException {
        probe("chain");
    }

    @Test
    void chainSingleton() throws IOException {
        probe("chainSingleton");
    }

    @Test
    void excludeClassInterceptors() throws IOException {
        probe("excludeClassInterceptors");
    }

    @Test
    void shortCircuit() throws IOException {
        probe("shortCircuit");
    }

    private void probe(String name) throws IOException {
        try (WebClient webClient = new WebClient()) {
            webClient.getOptions().setTimeout(0);
            TextPage page = webClient.getPage(base + "probe?name=" + name);
            String content = page.getContent();
            System.out.println("=== " + name + "\n" + content);
            assertTrue(content.startsWith("PASS"), content);
        }
    }
}
