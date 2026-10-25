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
package ee.omnifish.omnibeans.security;

import jakarta.security.jacc.PolicyContext;

import java.util.List;

import org.glassfish.exousia.DefaultPolicyContextHandler;

import static ee.omnifish.omnibeans.security.JakartaAuthorization.inContext;
import static jakarta.security.jacc.PolicyContext.PRINCIPAL_MAPPER;
import static jakarta.security.jacc.PolicyContext.SUBJECT;

/**
 * Gives the session beans' policy context the {@code SUBJECT} and {@code PRINCIPAL_MAPPER} of the web module's one,
 * through Exousia, the Jakarta Authorization implementation of the containers OmniBeans runs on.
 *
 * <p>
 * Jakarta Authorization registers a policy context handler per key, for all policy contexts at once. Exousia's
 * handler looks the object up per policy context (the one on the calling thread), in a table that each
 * {@link DefaultPolicyContextHandler} adds its policy context's entry to when it's constructed. Its own policy (as any
 * other) asks the handlers in the policy context it checks in, so the session beans' policy context needs entries of
 * its own. They delegate to the web module's policy context, which shares its principal-to-role mapping with the
 * session beans' one (they're linked).
 *
 * <p>
 * The table is static, so this must use the container's copy of Exousia.
 */
final class ExousiaHandlers {

    private static final List<String> KEYS = List.of(SUBJECT, PRINCIPAL_MAPPER);

    private ExousiaHandlers() {
    }

    /**
     * Gives a policy context the handlers of another one.
     *
     * @param webContextId the policy context to take the handlers from
     * @param contextId the policy context to give them to
     */
    static void register(String webContextId, String contextId) {
        for (String key : KEYS) {
            // Exousia's handler, which the web module's policy context installed, is already registered for the key
            DefaultPolicyContextHandler.register(contextId, key, () -> inContext(webContextId, () -> PolicyContext.get(key)));
        }
    }

    /**
     * Removes the handlers of a policy context.
     *
     * @param contextId the policy context
     */
    static void remove(String contextId) {
        DefaultPolicyContextHandler.removeAllForContextId(contextId);
    }
}
