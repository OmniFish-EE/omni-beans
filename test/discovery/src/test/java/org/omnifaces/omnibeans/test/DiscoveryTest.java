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
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.omnibeans.test.lib.LibSingleton;
import org.omnifaces.omnibeans.test.lib.LibStateless;

/**
 * Tests the discovery of classes that are only annotated {@code @Stateless} or {@code @Singleton}, in the web
 * application and in a library of it, in the supported configuration: each archive has a beans.xml with
 * {@code bean-discovery-mode="all"} (which Weld needs; Quarkus doesn't).
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class DiscoveryTest {

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        File beansXml = new File("src/main/webapp/WEB-INF", "beans.xml");

        return create(WebArchive.class)
                .addAsWebInfResource(beansXml)
                .addClasses(ProbeServlet.class, WarSingleton.class, WarStateless.class)
                .addAsLibrary(create(JavaArchive.class, "discovery-lib.jar")
                        .addAsManifestResource(beansXml, "beans.xml")
                        .addClasses(LibSingleton.class, LibStateless.class));
    }

    @Test
    void discovery() throws IOException {
        try (WebClient webClient = new WebClient()) {
            webClient.getOptions().setTimeout(0);
            TextPage page = webClient.getPage(base + "probe");
            String content = page.getContent();
            System.out.println("=== discovery\n" + content);
            assertTrue(content.contains("WarStateless: scope=Pooled hello=WarStateless"), content);
            assertTrue(content.contains("WarSingleton: scope=ApplicationScoped hello=WarSingleton"), content);
            assertTrue(content.contains("LibStateless: scope=Pooled hello=LibStateless"), content);
            assertTrue(content.contains("LibSingleton: scope=ApplicationScoped hello=LibSingleton"), content);
        }
    }
}
