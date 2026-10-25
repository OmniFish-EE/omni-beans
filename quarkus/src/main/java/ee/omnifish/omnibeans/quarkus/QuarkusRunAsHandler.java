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
package ee.omnifish.omnibeans.quarkus;

import java.util.concurrent.Callable;

import ee.omnifish.omnibeans.spi.RunAsHandler;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Runs session beans with {@code @RunAs} as their role on Quarkus. The role runs as the principal with the role's
 * name, with the identity that Quarkus' identity providers have for it without its credentials
 * ({@link TrustedAuthenticationRequest}), as GlassFish logs in a run-as principal. During the call, that identity is
 * Quarkus' current identity, so Quarkus' own security and Jakarta Authorization (when it takes the caller from
 * Quarkus, as Exousia does) both see it.
 */
@ApplicationScoped
public class QuarkusRunAsHandler implements RunAsHandler {

    @Inject
    private CurrentIdentityAssociation identityAssociation;

    @Inject
    private IdentityProviderManager identityProviderManager;

    /**
     * Creates the handler. Used by CDI.
     */
    public QuarkusRunAsHandler() {
    }

    @Override
    public <T> T runAs(String role, Callable<T> action) throws Exception {
        SecurityIdentity runAsIdentity = identityProviderManager.authenticateBlocking(new TrustedAuthenticationRequest(role));
        SecurityIdentity callerIdentity = identityAssociation.getIdentity();

        identityAssociation.setIdentity(runAsIdentity);
        try {
            return action.call();
        } finally {
            identityAssociation.setIdentity(callerIdentity);
        }
    }
}
