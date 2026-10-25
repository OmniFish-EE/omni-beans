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
package ee.omnifish.omnibeans.spi;

import java.util.concurrent.Callable;

/**
 * Runs the business methods of a session bean with {@code @RunAs} as the identity of its run-as role. Only the
 * container can switch the caller's identity, so the container (or a library that integrates it) provides this as a
 * bean. OmniBeans calls it around each call of such a bean's business methods, after authorizing the call for the
 * caller.
 * <p>
 * The identity is the one the container has for the role's run-as principal, so that it has that principal's roles,
 * as when the principal logs in. Which principal a role runs as is up to the container; the name of the role itself
 * is the obvious default.
 */
public interface RunAsHandler {

    /**
     * Runs the action as the identity of the given run-as role, and restores the caller's identity afterwards.
     *
     * @param <T> the type of the action's result
     * @param role the run-as role
     * @param action the action
     * @return what the action returned
     * @throws Exception what the action threw
     */
    <T> T runAs(String role, Callable<T> action) throws Exception;
}
