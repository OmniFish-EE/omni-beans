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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import ee.omnifish.omnibeans.security.SecurityDescriptor;
import ee.omnifish.omnibeans.security.SecurityDescriptor.Access;
import ee.omnifish.omnibeans.security.SecurityDescriptor.MethodPermission;
import ee.omnifish.omnibeans.security.buildtime.SecurityData.MethodData;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * Creates the {@link SecurityDescriptor} of a session bean from the parameters of its synthetic bean, which
 * carry what the build read from the bean's annotations.
 *
 * <p>
 * The parameters are strings and arrays of strings: {@code beanClass}, {@code name}, {@code runAs} (empty if none),
 * {@code secured}, the {@code roles} of the methods, and per business method its name ({@code methodNames}), its
 * parameter types joined by commas ({@code methodParameters}) and its access ({@code methodAccess}:
 * {@code excluded}, {@code unchecked}, or {@code roles:} followed by the indexes of its roles in {@code roles}, joined
 * by commas).
 */
public class SecurityDescriptorCreator implements SyntheticBeanCreator<SecurityDescriptor> {

    private static final String EXCLUDED = "excluded";
    private static final String UNCHECKED = "unchecked";
    private static final String ROLES = "roles:";

    /**
     * Encodes the security of a session bean to parameters for a synthetic bean
     *
     * @param bean the synthetic bean
     * @param securityData the security of the session bean
     */
    public static void withParams(SyntheticBeanBuilder<SecurityDescriptor> bean, SecurityData securityData) {
        List<String> allRolesForBean = findAllRolesForBean(securityData);

        bean.withParam("beanClass", securityData.beanClass())
            .withParam("name", securityData.name())
            .withParam("runAs", securityData.runAs() != null ? securityData.runAs() : "")
            .withParam("secured", securityData.secured())
            .withParam("allRolesForBean", allRolesForBean.toArray(String[]::new))
            .withParam("methodNames", encodeMethodParts(securityData, methodData -> methodData.name()))
            .withParam("methodParameters", encodeMethodParts(securityData, methodData -> join(methodData.parameterTypes())))
            .withParam("methodAccess", encodeMethodParts(securityData, methodData -> encodeAccess(methodData, allRolesForBean)));
    }

    /**
     * Decodes the security of a session bean from parameters of a synthetic bean
     */
    @Override
    public SecurityDescriptor create(Instance<Object> lookup, Parameters params) {
        String beanClass = params.get("beanClass", String.class);
        String name = params.get("name", String.class);
        String runAs = params.get("runAs", String.class);
        Boolean secured = params.get("secured", Boolean.class);
        String[] allRolesForBean = params.get("allRolesForBean", String[].class);
        String[] methodNames = params.get("methodNames", String[].class);
        String[] methodParameters = params.get("methodParameters", String[].class);
        String[] methodAccess = params.get("methodAccess", String[].class);

        List<MethodPermission> methodPermissions = new ArrayList<>();
        for (int i = 0; i < methodNames.length; i++) {
            methodPermissions.add(new MethodPermission(
                methodNames[i],
                split(methodParameters[i]),
                decodeAccess(methodAccess[i]),
                decodeRoles(methodAccess[i], allRolesForBean)));
        }

        return new SecurityDescriptor(
            beanClass,
            name,
            runAs.isEmpty() ? null : runAs,
            secured,
            methodPermissions);
    }

    private static List<String> findAllRolesForBean(SecurityData securityData) {
        return new ArrayList<>(
            securityData.methods()
                        .stream()
                        .flatMap(method -> method.roles().stream())
                        .collect(toCollection(LinkedHashSet::new)));
    }

    private static String[] encodeMethodParts(SecurityData securityData, Function<? super MethodData, ? extends String> mapper) {
        return securityData.methods().stream().map(mapper).toArray(String[]::new);
    }

    private static String encodeAccess(MethodData method, List<String> roles) {
        return switch (method.access()) {
            case EXCLUDED -> EXCLUDED;
            case UNCHECKED -> UNCHECKED;
            case ROLES -> ROLES + method.roles().stream().map(role -> String.valueOf(roles.indexOf(role))).collect(joining(","));
        };
    }

    private static Access decodeAccess(String encodedAccess) {
        return
            encodedAccess.equals(EXCLUDED) ?
                Access.EXCLUDED :
            encodedAccess.equals(UNCHECKED) ?
                Access.UNCHECKED :
                Access.ROLES;
    }

    private static Set<String> decodeRoles(String access, String[] roles) {
        if (!access.startsWith(ROLES)) {
            return Set.of();
        }

        return Arrays.stream(split(access.substring(ROLES.length())))
                     .map(index -> roles[parseInt(index)])
                     .collect(toUnmodifiableSet());
    }

    public static String join(List<String> toJoin) {
        return String.join(",", toJoin);
    }

    /**
     * Splits a comma-separated list. An empty string is an empty list, not a list with an empty element: a method
     * without parameters isn't one whose parameters are unspecified, which {@code null} would mean.
     */
    public static String[] split(String joined) {
        return joined.isEmpty() ? new String[0] : joined.split(",");
    }
}
