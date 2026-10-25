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

import jakarta.security.jacc.EJBMethodPermission;
import jakarta.security.jacc.EJBRoleRefPermission;
import jakarta.security.jacc.PolicyConfiguration;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ee.omnifish.omnibeans.security.SecurityDescriptor.MethodPermission;
import ee.omnifish.omnibeans.security.buildtime.SecurityDescriptorCreator;

import static ee.omnifish.omnibeans.security.SecurityDescriptor.Access.EXCLUDED;
import static ee.omnifish.omnibeans.security.SecurityDescriptor.Access.ROLES;
import static ee.omnifish.omnibeans.security.SecurityDescriptor.Access.UNCHECKED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The security descriptor of a session bean, and its statements: the parameter type names that the build writes (see
 * SecurityData) give the same {@link EJBMethodPermission} as the method a call is made through, the access of each
 * method is found by that method, and each method and application role gets its statement.
 */
class SecurityDescriptorTest {

    static class Nested {
    }

    static class Base<T extends Number> {

        public void generic(T value) {
        }
    }

    // The parameter types, and their names as the build writes them
    @SuppressWarnings("unused")
    static class Bean extends Base<Integer> {

        public void none() {
        }

        public void primitives(int i, long l, boolean b) {
        }

        public void arrays(int[] i, String[] s, long[][] l, Nested[][][] n) {
        }

        public void nested(Nested n, Map.Entry<String, String> e) {
        }

        public <U> void typeVariable(U unbounded) {
        }

        public <U extends CharSequence & Comparable<U>> void boundedTypeVariable(U bounded, U[] array) {
        }

        public void parameterized(List<String> list, Map<String, List<Integer>> map, List<?>[] array) {
        }

        public void overloaded(int i) {
        }

        public void overloaded(String s) {
        }
    }

    private static final Map<String, String[]> TYPE_NAMES = Map.of(
        "none", new String[0],
        "primitives", new String[] { "int", "long", "boolean" },
        "arrays", new String[] { "int[]", "java.lang.String[]", "long[][]", Nested.class.getName() + "[][][]" },
        "nested", new String[] { Nested.class.getName(), "java.util.Map$Entry" },
        "typeVariable", new String[] { "java.lang.Object" },
        "boundedTypeVariable", new String[] { "java.lang.CharSequence", "java.lang.CharSequence[]" },
        "parameterized", new String[] { "java.util.List", "java.util.Map", "java.util.List[]" },
        "generic", new String[] { "java.lang.Number" });

    @Test
    void typeNamesGiveTheMethodsPermission() throws Exception {
        for (Map.Entry<String, String[]> method : TYPE_NAMES.entrySet()) {
            Method reflected = findMethod(method.getKey());
            EJBMethodPermission expected = new EJBMethodPermission("Bean", "Local", reflected);

            assertArrayEquals(method.getValue(), SecurityDescriptor.getParameterTypes(reflected), method.getKey());
            assertEquals(expected, new EJBMethodPermission("Bean", method.getKey(), "Local", method.getValue()), method.getKey());
            assertTrue(new EJBMethodPermission("Bean", method.getKey(), "Local", method.getValue()).implies(expected), method.getKey());
        }
    }

    @Test
    void noParametersAreNotUnspecifiedParameters() throws Exception {
        String[] parameterTypes = SecurityDescriptorCreator.split("");
        assertEquals(0, parameterTypes.length);

        Method none = Bean.class.getMethod("none");
        Method overloaded = Bean.class.getMethod("overloaded", int.class);
        EJBMethodPermission noParameters = new EJBMethodPermission("Bean", "overloaded", "Local", parameterTypes);

        // A permission without parameters is for the method without parameters only; with null, for all overloads
        assertEquals(new EJBMethodPermission("Bean", "Local", none), new EJBMethodPermission("Bean", "none", "Local", parameterTypes));
        assertFalse(noParameters.implies(new EJBMethodPermission("Bean", "Local", overloaded)));
        assertTrue(new EJBMethodPermission("Bean", "overloaded", "Local", null).implies(new EJBMethodPermission("Bean", "Local", overloaded)));
        assertArrayEquals(new String[] { "int", "java.lang.String" }, SecurityDescriptorCreator.split("int,java.lang.String"));
    }

    @Test
    void overloadsKeepTheirOwnAccess() throws Exception {
        SecurityDescriptor descriptor = new SecurityDescriptor(Bean.class.getName(), "Bean", null, true, List.of(
            new MethodPermission("overloaded", new String[] { "int" }, EXCLUDED, Set.of()),
            new MethodPermission("overloaded", new String[] { "java.lang.String" }, UNCHECKED, Set.of()),
            new MethodPermission("none", new String[0], ROLES, Set.of("manager"))));

        assertFalse(descriptor.isUnchecked(Bean.class.getMethod("overloaded", int.class)));
        assertTrue(descriptor.isUnchecked(Bean.class.getMethod("overloaded", String.class)));
        assertFalse(descriptor.isUnchecked(Bean.class.getMethod("none")));

        // Not a business method the build saw
        assertTrue(descriptor.isUnchecked(Bean.class.getMethod("primitives", int.class, long.class, boolean.class)));

        List<String> statements = new ArrayList<>();
        JakartaAuthorization.addPermissionsToPolicy(descriptor, Set.of("manager", "employee"), recording(statements));

        assertTrue(statements.contains("excluded " + new EJBMethodPermission("Bean", "Local", Bean.class.getMethod("overloaded", int.class))));
        assertTrue(statements.contains("unchecked " + new EJBMethodPermission("Bean", "Local", Bean.class.getMethod("overloaded", String.class))));
        assertTrue(statements.contains("manager " + new EJBMethodPermission("Bean", "Local", Bean.class.getMethod("none"))));
        assertTrue(statements.contains("manager " + new EJBRoleRefPermission("Bean", "manager")));
        assertTrue(statements.contains("employee " + new EJBRoleRefPermission("Bean", "employee")));
        assertEquals(5, statements.size());
        assertNotEquals(statements.get(0), statements.get(1));
    }

    private static Method findMethod(String name) {
        for (Method method : Bean.class.getMethods()) {
            if (method.getName().equals(name) && !method.isBridge()) {
                return method;
            }
        }
        throw new AssertionError("No method " + name);
    }

    // Records the statements added to it as "excluded", "unchecked" or the role, followed by the permission
    private static PolicyConfiguration recording(List<String> statements) {
        return (PolicyConfiguration) Proxy.newProxyInstance(PolicyConfiguration.class.getClassLoader(), new Class<?>[] { PolicyConfiguration.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "addToExcludedPolicy" -> statements.add("excluded " + args[0]);
                    case "addToUncheckedPolicy" -> statements.add("unchecked " + args[0]);
                    case "addToRole" -> statements.add(args[0] + " " + args[1]);
                    default -> throw new UnsupportedOperationException(method.getName());
                }
                return null;
            });
    }
}
