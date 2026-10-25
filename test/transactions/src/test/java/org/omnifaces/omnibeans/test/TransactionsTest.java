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
 * Tests the transactions of session beans. With Bean Managed Transactions, a transaction a bean leaves open is rolled
 * back, the caller gets an EJBException, and a stateless bean's instance is discarded. With Container Managed
 * Transactions, each transaction attribute behaves as the Enterprise Beans specification requires.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class TransactionsTest {

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return create(WebArchive.class)
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "beans.xml"))
                .addClasses(
                    CompletionRecorder.class,
                    Events.class,
                    FutureProblem.class,
                    ProbeServlet.class,
                    SingletonBeanManaged.class,
                    SingletonContainerManaged.class,
                    StatelessBeanManaged.class,
                    StatelessContainerManaged.class);
    }

    @Test
    void commit() throws IOException {
        probe("commit");
    }

    @Test
    void leak() throws IOException {
        probe("leak");
    }

    @Test
    void leakSingleton() throws IOException {
        probe("leakSingleton");
    }

    @Test
    void leakAsync() throws IOException {
        probe("leakAsync");
    }

    @Test
    void required() throws IOException {
        probe("required");
    }

    @Test
    void requiredSingleton() throws IOException {
        probe("requiredSingleton");
    }

    @Test
    void requiresNew() throws IOException {
        probe("requiresNew");
    }

    @Test
    void mandatory() throws IOException {
        probe("mandatory");
    }

    @Test
    void supports() throws IOException {
        probe("supports");
    }

    @Test
    void notSupported() throws IOException {
        probe("notSupported");
    }

    @Test
    void never() throws IOException {
        probe("never");
    }

    @Test
    void asyncInCallerTransaction() throws IOException {
        probe("asyncInCallerTransaction");
    }

    @Test
    void beanManagedInCallerTransaction() throws IOException {
        probe("beanManagedInCallerTransaction");
    }

    @Test
    void futureFailsUnchecked() throws IOException {
        probe("futureFailsUnchecked");
    }

    @Test
    void futureFailsChecked() throws IOException {
        probe("futureFailsChecked");
    }

    /**
     * A call of a bean's own business method on this instance: ArC (Quarkus) intercepts it, so in a bean with Bean
     * Managed Transactions it runs without the bean's transaction; Weld (Piranha) doesn't, as an Enterprise Beans
     * container doesn't. The test profile says which applies.
     */
    @Test
    void selfInvocation() throws IOException {
        String expected = Boolean.getBoolean("omnibeans.test.selfInvocationIntercepted") ? "intercepted" : "not intercepted";
        String content = probe("selfInvocation");
        assertTrue(content.contains("event: self-invocation " + expected + "\n"), content);
    }

    private String probe(String name) throws IOException {
        try (WebClient webClient = new WebClient()) {
            webClient.getOptions().setTimeout(0);
            TextPage page = webClient.getPage(base + "probe?name=" + name);
            String content = page.getContent();
            System.out.println("=== " + name + "\n" + content);
            assertTrue(content.startsWith("PASS"), content);
            return content;
        }
    }
}
