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
 * Tests the locks of singleton beans: read locks are shared, write locks aren't, a method's lock type and access
 * timeout override the class', a lock that isn't acquired in time gives a ConcurrentAccessTimeoutException (a
 * ConcurrentAccessException when waiting isn't permitted), a call that holds the read lock and needs the write lock
 * again gets an IllegalLoopbackException at once, and Bean Managed Concurrency doesn't lock.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class LockingTest {

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return create(WebArchive.class)
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "beans.xml"))
                .addClasses(
                    BeanManagedSingleton.class,
                    Latches.class,
                    LockedSingleton.class,
                    LoopbackHelper.class,
                    ProbeServlet.class);
    }

    @Test
    void readersShare() throws IOException {
        probe("readersShare");
    }

    @Test
    void writerWaitsForReaders() throws IOException {
        probe("writerWaitsForReaders");
    }

    @Test
    void readerWaitsForWriter() throws IOException {
        probe("readerWaitsForWriter");
    }

    @Test
    void notPermitted() throws IOException {
        probe("notPermitted");
    }

    @Test
    void beanManagedConcurrency() throws IOException {
        probe("beanManagedConcurrency");
    }

    @Test
    void loopback() throws IOException {
        probe("loopback");
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
