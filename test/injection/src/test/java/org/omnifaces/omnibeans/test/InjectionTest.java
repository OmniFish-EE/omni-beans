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
 * Tests {@code @EJB} injection: fields and setters, with a bean name (two beans have the same business interface) and
 * without, in a session bean, in another CDI bean and in a servlet; a stateless bean that injects itself, whose calls
 * through that reference get their container services; and {@code @Resource} injection of a session bean's contexts.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class InjectionTest {

    // Piranha creates a servlet from its unmodified class (Weld's Unmanaged), so it doesn't see the mapping of @EJB
    private static final boolean SERVLET_EJB_INJECTED = Boolean.getBoolean("omnibeans.test.servletEjbInjected");

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return create(WebArchive.class)
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "beans.xml"))
                .addClasses(
                    Counter.class,
                    DutchGreeter.class,
                    EnglishGreeter.class,
                    Greeter.class,
                    GreetingFacade.class,
                    GreetingHolder.class,
                    ProbeServlet.class,
                    SelfInjected.class);
    }

    @Test
    void injection() throws IOException {
        try (WebClient webClient = new WebClient()) {
            webClient.getOptions().setTimeout(0);
            TextPage page = webClient.getPage(base + "probe");
            String content = page.getContent();
            System.out.println("=== injection\n" + content);
            assertTrue(content.contains("facade: Hello,Hallo,1"), content);
            assertTrue(content.contains("holder: Hallo"), content);
            assertTrue(content.contains("self: new transaction"), content);
            assertTrue(content.contains("contexts: false/ANONYMOUS/false,false/ANONYMOUS/false"), content);
            assertTrue(content.contains(SERVLET_EJB_INJECTED ? "servlet: Hello" : "servlet: not injected"), content);
        }
    }
}
