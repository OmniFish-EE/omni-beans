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
package ee.omnifish.omnibeans.security.buildtime;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

import java.util.Set;

/**
 * Creates the {@link ApplicationRoles} from the parameter of its synthetic bean, {@code roles}, which carries the
 * roles the build found.
 */
public class ApplicationRolesCreator implements SyntheticBeanCreator<ApplicationRoles> {

    public static void withParams(SyntheticBeanBuilder<ApplicationRoles> bean, Set<String> applicationRoles) {
        bean.withParam("roles", applicationRoles.toArray(String[]::new));
    }

    @Override
    public ApplicationRoles create(Instance<Object> lookup, Parameters params) {
        return new ApplicationRoles(Set.of(params.get("roles", String[].class)));
    }
}
