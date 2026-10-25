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

import jakarta.annotation.security.RunAs;
import jakarta.ejb.Singleton;
import jakarta.enterprise.inject.Typed;

/**
 * A session bean that inherits a method for role {@code caller}, and runs as role {@code runner}, which callers don't
 * have. Both OmniBeans and Quarkus (for the inherited annotation) check the caller before the bean runs as the role.
 * Only its own type, so that injecting {@link GuardedService} isn't ambiguous.
 */
@Singleton
@RunAs("runner")
@Typed(GuardedEjb.class)
public class GuardedEjb extends GuardedService {
}
