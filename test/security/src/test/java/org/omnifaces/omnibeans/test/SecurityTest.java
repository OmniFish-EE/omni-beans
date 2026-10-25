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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * Tests the security of session beans through Jakarta Authorization: method permissions from {@code RolesAllowed},
 * {@code PermitAll} and {@code DenyAll} (also for overloads, and through a bridge method), the caller and its roles in
 * the session context (also for a role that only another bean names), run-as (the bean itself sees its caller, the
 * beans it calls the role; the caller's identity is restored after a failure), an inherited Quarkus check before
 * run-as, calls from a listener at startup, and the refusal of role-protected asynchronous methods.
 * <p>
 * Users: alice (roles caller, manager, auditor), bob (caller), and runner (runner), the run-as identity.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class SecurityTest {

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return create(WebArchive.class)
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "beans.xml"))
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF", "web.xml"))
                .addClasses(
                    AsyncSecuredBean.class,
                    AuditBean.class,
                    Calls.class,
                    Echo.class,
                    EchoBean.class,
                    GuardedEjb.class,
                    GuardedService.class,
                    ProbeServlet.class,
                    RunAsBean.class,
                    SecuredBean.class,
                    StartupListener.class,
                    StartupResults.class,
                    UnsecuredBean.class);
    }

    @Test
    void unauthenticated() throws IOException {
        Map<String, String> results = probe("probe", null);

        assertEquals("open=open,managers=EJBAccessException", results.get("startup"));
        assertEquals("ANONYMOUS/false/false", results.get("whoAmI"));
        assertEquals("open", results.get("open"));
        assertEquals("EJBAccessException", results.get("managers"));
        assertEquals("EJBAccessException", results.get("nobody"));
        assertEquals("EJBAccessException", results.get("overloadedInt"));
        assertEquals("String", results.get("overloadedString"));
        assertEquals("EJBAccessException", results.get("echo"));
        assertEquals("ANONYMOUS>runner/true", results.get("chain"));
        // The called bean fails in the run-as bean's transaction; afterwards, the caller is the caller again
        assertEquals("EJBTransactionRolledbackException", results.get("failing"));
        assertEquals("ANONYMOUS/false/false", results.get("restored"));
        assertEquals("UnauthorizedException", results.get("guardedService"));
        assertEquals("EJBAccessException", results.get("guardedEjb"));
        assertEquals("EJBAccessException", results.get("async"));
    }

    @Test
    void manager() throws IOException {
        Map<String, String> results = probe("secured/probe", "alice");

        assertEquals("alice/true/true", results.get("whoAmI"));
        assertEquals("open", results.get("open"));
        assertEquals("managers", results.get("managers"));
        assertEquals("EJBAccessException", results.get("nobody"));
        assertEquals("int", results.get("overloadedInt"));
        assertEquals("String", results.get("overloadedString"));
        assertEquals("echoed", results.get("echo"));
        assertEquals("alice>runner/true", results.get("chain"));
        assertEquals("EJBTransactionRolledbackException", results.get("failing"));
        assertEquals("alice/true/true", results.get("restored"));
        assertEquals("guarded", results.get("guardedService"));
        assertEquals("guarded", results.get("guardedEjb"));
        assertEquals("EJBAccessException", results.get("async"));
    }

    @Test
    void caller() throws IOException {
        Map<String, String> results = probe("secured/probe", "bob");

        assertEquals("bob/false/false", results.get("whoAmI"));
        assertEquals("EJBAccessException", results.get("managers"));
        assertEquals("EJBAccessException", results.get("overloadedInt"));
        assertEquals("String", results.get("overloadedString"));
        assertEquals("EJBAccessException", results.get("echo"));
        assertEquals("bob>runner/true", results.get("chain"));
        assertEquals("bob/false/false", results.get("restored"));
        assertEquals("guarded", results.get("guardedService"));

        // Bob isn't in role runner: Quarkus' inherited check must see him, not the run-as identity
        assertEquals("guarded", results.get("guardedEjb"));
    }

    // The probe's lines, as name and result; the user's password is its name
    private Map<String, String> probe(String path, String user) throws IOException {
        try (WebClient webClient = new WebClient()) {
            webClient.getOptions().setTimeout(0);
            if (user != null) {
                webClient.addRequestHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + user).getBytes(UTF_8)));
            }

            TextPage page = webClient.getPage(base + path);
            String content = page.getContent();
            System.out.println("=== " + path + " as " + user + "\n" + content);

            Map<String, String> results = new LinkedHashMap<>();
            for (String line : content.split("\n")) {
                int separator = line.indexOf(": ");
                if (separator > 0) {
                    results.put(line.substring(0, separator), line.substring(separator + 2));
                }
            }
            return results;
        }
    }
}
