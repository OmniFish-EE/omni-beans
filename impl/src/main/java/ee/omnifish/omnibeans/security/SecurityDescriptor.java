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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ee.omnifish.omnibeans.security.buildtime.SecurityDescriptorCreator;

/**
 * The security of a session bean as its annotations declare it, read at build time: its name, its run-as role, whether
 * it's secured, and who may call each of its business methods. {@link JakartaAuthorization} translates it into
 * Jakarta Authorization statements: an {@link EJBMethodPermission} per business method, and an
 * {@link EJBRoleRefPermission} per application role.
 *
 * <p>
 * A synthetic bean per session bean, created by {@link SecurityDescriptorCreator}.
 */
public final class SecurityDescriptor {

    /**
     * Who may call a business method: nobody, everybody, or the callers in one of the method's roles.
     */
    public enum Access {
        EXCLUDED,
        UNCHECKED,
        ROLES
    }

    /**
     * Who may call a business method.
     *
     * @param name the method's name
     * @param parameterTypes the erased types of its parameters, as {@link EJBMethodPermission} names them
     * @param access who may call it
     * @param roles the roles that may call it, for {@link Access#ROLES}
     */
    public record MethodPermission(String name, String[] parameterTypes, Access access, Set<String> roles) {
    }

    private final String beanClass;
    private final String name;
    private final String runAs;
    private final boolean secured;
    // Per signature (name and parameter types) of a business method, who may call it
    private final Map<String, MethodPermission> methodPermissions = new HashMap<>();

    /**
     * Creates the descriptor.
     *
     * @param beanClass the binary name of the bean class
     * @param name the bean's name
     * @param runAs the bean's run-as role, or null if it runs as its caller
     * @param secured whether the annotations secure the bean
     * @param methodPermissions who may call each business method
     */
    public SecurityDescriptor(String beanClass, String name, String runAs, boolean secured, List<MethodPermission> methodPermissions) {
        this.beanClass = beanClass;
        this.name = name;
        this.runAs = runAs;
        this.secured = secured;
        for (MethodPermission methodPermission : methodPermissions) {
            this.methodPermissions.put(getSignature(methodPermission.name(), methodPermission.parameterTypes()), methodPermission);
        }
    }

    /**
     * Returns the binary name of the bean class.
     */
    String getBeanClass() {
        return beanClass;
    }

    /**
     * Returns the bean's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the bean's run-as role, or null if it runs as its caller.
     */
    String getRunAs() {
        return runAs;
    }

    /**
     * Returns whether the annotations secure the bean, in which case its calls are authorized.
     */
    boolean isSecured() {
        return secured;
    }

    /**
     * Returns who may call each business method.
     */
    Collection<MethodPermission> getMethodPermissions() {
        return methodPermissions.values();
    }

    /**
     * Tells whether the annotations let everybody call a method.
     *
     * @param method a business method
     * @return whether the annotations let everybody call it
     */
    boolean isUnchecked(Method method) {
        MethodPermission methodPermission = methodPermissions.get(getSignature(method.getName(), getParameterTypes(method)));
        return methodPermission == null || methodPermission.access() == Access.UNCHECKED;
    }

    /**
     * Returns the names of a method's parameter types as {@link EJBMethodPermission} names them: the binary name, and
     * for an array the one of its component type followed by a {@code []} per dimension.
     *
     * @param method a method
     * @return the names of its parameter types
     */
    static String[] getParameterTypes(Method method) {
        return Arrays.stream(method.getParameterTypes()).map(SecurityDescriptor::getTypeName).toArray(String[]::new);
    }

    private static String getTypeName(Class<?> type) {
        return type.isArray() ? getTypeName(type.getComponentType()) + "[]" : type.getName();
    }

    private static String getSignature(String methodName, String[] parameterTypes) {
        return methodName + Arrays.toString(parameterTypes);
    }
}
