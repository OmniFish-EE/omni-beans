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
 * Tests how session beans handle exceptions: system exceptions discard a stateless bean's instance and reach the caller
 * as EJBException, application exceptions reach the caller as they are.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class ExceptionsTest {

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return create(WebArchive.class)
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "beans.xml"))
                .addClasses(
                    ApplicationProblem.class,
                    ApplicationRuntimeProblem.class,
                    Events.class,
                    InheritedApplicationRuntimeProblem.class,
                    NotInheritedApplicationRuntimeProblem.class,
                    NotInheritingSystemProblem.class,
                    ProbeServlet.class,
                    SingletonThrower.class,
                    StatelessThrower.class,
                    TransactionalCaller.class);
    }

    @Test
    void systemException() throws IOException {
        probe("systemException");
    }

    @Test
    void error() throws IOException {
        probe("error");
    }

    @Test
    void notInheritingSystemException() throws IOException {
        probe("notInheritingSystemException");
    }

    @Test
    void ejbException() throws IOException {
        probe("ejbException");
    }

    @Test
    void applicationException() throws IOException {
        probe("applicationException");
    }

    @Test
    void applicationRuntimeException() throws IOException {
        probe("applicationRuntimeException");
    }

    @Test
    void inheritedApplicationRuntimeException() throws IOException {
        probe("inheritedApplicationRuntimeException");
    }

    @Test
    void singletonSystemException() throws IOException {
        probe("singletonSystemException");
    }

    @Test
    void callerTransaction() throws IOException {
        probe("callerTransaction");
    }

    @Test
    void asyncSystemException() throws IOException {
        probe("asyncSystemException");
    }

    @Test
    void asyncApplicationException() throws IOException {
        probe("asyncApplicationException");
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
