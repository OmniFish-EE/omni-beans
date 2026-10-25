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

import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.AnnotationTarget;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ee.omnifish.omnibeans.security.SecurityDescriptor.Access;

import static ee.omnifish.omnibeans.security.SecurityDescriptor.Access.EXCLUDED;
import static ee.omnifish.omnibeans.security.SecurityDescriptor.Access.ROLES;
import static ee.omnifish.omnibeans.security.SecurityDescriptor.Access.UNCHECKED;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

/**
 * The security of a session bean as its annotations declare it, read at build time as an Enterprise Beans container
 * reads them: its name, its run-as role, and who may call each of its business methods. Read before OmniBeans removes
 * the annotations from the bean's CDI view.
 *
 * @param beanClass the binary name of the bean class
 * @param name the bean's name: the {@code name} of its {@link Stateless} or {@link Singleton}, else its simple class
 *        name
 * @param runAs the bean's run-as role, or null if it runs as its caller
 * @param secured whether the annotations secure the bean: {@link RolesAllowed}, {@link PermitAll}, {@link DenyAll} or
 *        {@link RunAs} on the class, a superclass or a business method
 * @param methods who may call each business method
 * @param roles the roles the bean's annotations name: those of its {@link DeclareRoles} and its {@link RolesAllowed}
 */
public record SecurityData(String beanClass, String name, String runAs, boolean secured, List<MethodData> methods, Set<String> roles) {

    /**
     * Who may call a business method.
     *
     * @param name the method's name
     * @param parameterTypes the erased types of its parameters, as {@code EJBMethodPermission} names them
     * @param access who may call it
     * @param roles the roles that may call it, for {@link Access#ROLES}
     */
    record MethodData(String name, List<String> parameterTypes, Access access, Set<String> roles) {
    }

    // The annotations that grant the business methods, and the run-as role
    private static final List<Class<? extends java.lang.annotation.Annotation>> SECURITY_ANNOTATIONS = List.of(
        RolesAllowed.class,
        PermitAll.class,
        DenyAll.class,
        RunAs.class);

    /**
     * Tells whether a class is a session bean.
     *
     * @param classInfo the class
     * @return whether it's a {@link Stateless} or {@link Singleton} session bean
     */
    public static boolean isSessionBean(ClassInfo classInfo) {
        return classInfo.hasAnnotation(Stateless.class) || classInfo.hasAnnotation(Singleton.class);
    }

    /**
     * Reads the security of a session bean.
     *
     * @param classInfo the bean class
     * @return its security
     */
    public static SecurityData read(ClassInfo classInfo) {
        AnnotationInfo runAs = classInfo.annotation(RunAs.class);

        // The class and its superclasses, the most specific first
        Map<String, Integer> depths = new HashMap<>();
        boolean secured = false;
        Set<String> roles = new LinkedHashSet<>();
        int depth = 0;
        for (ClassInfo type = classInfo; type != null && !type.name().equals(Object.class.getName()); type = type.superClassDeclaration()) {
            depths.put(type.name(), depth++);
            secured |= hasSecurityAnnotation(type);
            roles.addAll(getRoles(type));
        }

        // Each business method as the most specific class declares it
        Map<String, MethodInfo> businessMethods = new LinkedHashMap<>();
        for (MethodInfo method : classInfo.methods()) {
            if (isBusinessMethod(method)) {
                businessMethods.merge(getSignature(method), method,
                    (current, candidate) -> getDepth(depths, candidate) < getDepth(depths, current) ? candidate : current);
            }
        }

        List<MethodData> methods = businessMethods.values().stream()
            .map(SecurityData::getMethodData)
            .toList();

        for (MethodData method : methods) {
            secured |= method.access() != UNCHECKED || hasSecurityAnnotation(businessMethods.get(getSignature(method)));
            roles.addAll(method.roles());
        }

        return new SecurityData(
            classInfo.name(),
            getName(classInfo),
            runAs != null ? runAs.value().asString() : null,
            secured,
            methods,
            Set.copyOf(roles));
    }

    /**
     * Returns the roles that the annotations of a class and its own methods name: those of its {@link DeclareRoles} and
     * its {@link RolesAllowed}.
     *
     * @param classInfo the class
     * @return the roles
     */
    public static Set<String> getRoles(ClassInfo classInfo) {
        Set<String> roles = new LinkedHashSet<>();
        roles.addAll(getRoles(classInfo.annotation(DeclareRoles.class)));
        roles.addAll(getRoles(classInfo.annotation(RolesAllowed.class)));

        for (MethodInfo method : classInfo.methods()) {
            if (method.declaringClass().name().equals(classInfo.name())) {
                roles.addAll(getRoles(method.annotation(RolesAllowed.class)));
            }
        }

        return roles;
    }

    private static String getName(ClassInfo classInfo) {
        AnnotationInfo sessionBean = classInfo.hasAnnotation(Stateless.class) ? classInfo.annotation(Stateless.class) : classInfo.annotation(Singleton.class);
        String name = sessionBean.member("name").asString();
        return name.isEmpty() ? classInfo.simpleName() : name;
    }

    // Public instance methods, except those of Object; bridge and synthetic methods aren't listed
    private static boolean isBusinessMethod(MethodInfo method) {
        int modifiers = method.modifiers();

        return
            !method.isConstructor() &&
            isPublic(modifiers) &&
            !isStatic(modifiers) &&
            !isAbstract(modifiers) &&
            !method.declaringClass().name().equals(Object.class.getName());
    }

    // Methods of interfaces (default methods) are less specific than those of any class
    private static int getDepth(Map<String, Integer> depths, MethodInfo method) {
        return depths.getOrDefault(method.declaringClass().name(), MAX_VALUE);
    }

    // The method's own annotation applies, else the one of the class that declares the method, else it's unchecked
    private static MethodData getMethodData(MethodInfo method) {
        MethodData methodData = getMethodData(method, method);
        if (methodData == null) {
            methodData = getMethodData(method, method.declaringClass());
        }

        return methodData != null ? methodData : new MethodData(method.name(), getParameterTypes(method), UNCHECKED, Set.of());
    }

    private static MethodData getMethodData(MethodInfo method, AnnotationTarget annotated) {
        if (annotated.hasAnnotation(DenyAll.class)) {
            return new MethodData(method.name(), getParameterTypes(method), EXCLUDED, Set.of());
        }

        if (annotated.hasAnnotation(PermitAll.class)) {
            return new MethodData(method.name(), getParameterTypes(method), UNCHECKED, Set.of());
        }

        AnnotationInfo rolesAllowed = annotated.annotation(RolesAllowed.class);
        if (rolesAllowed != null) {
            return new MethodData(method.name(), getParameterTypes(method), ROLES, Set.copyOf(getRoles(rolesAllowed)));
        }

        return null;
    }

    private static boolean hasSecurityAnnotation(AnnotationTarget annotated) {
        return SECURITY_ANNOTATIONS.stream().anyMatch(annotated::hasAnnotation);
    }

    private static List<String> getRoles(AnnotationInfo annotation) {
        if (annotation == null) {
            return List.of();
        }

        return annotation.value().asArray().stream().map(AnnotationMember::asString).toList();
    }

    private static String getSignature(MethodInfo method) {
        return method.name() + getParameterTypes(method);
    }

    private static String getSignature(MethodData method) {
        return method.name() + method.parameterTypes();
    }

    private static List<String> getParameterTypes(MethodInfo method) {
        return method.parameters().stream().map(parameter -> getTypeName(parameter.type())).toList();
    }

    /**
     * Returns the name of a parameter type as {@code EJBMethodPermission} names it: the binary name of its erasure, and
     * for an array the one of its component type followed by a {@code []} per dimension.
     */
    private static String getTypeName(Type type) {
        return switch (type.kind()) {
            case PRIMITIVE -> type.asPrimitive().name();
            case CLASS -> type.asClass().declaration().name();
            case ARRAY -> getTypeName(type.asArray().componentType()) + "[]";
            case PARAMETERIZED_TYPE -> type.asParameterizedType().genericClass().declaration().name();
            case TYPE_VARIABLE -> {
                List<Type> bounds = type.asTypeVariable().bounds();
                yield bounds.isEmpty() ? Object.class.getName() : getTypeName(bounds.get(0));
            }
            default -> throw new IllegalArgumentException("Not a parameter type: " + type);
        };
    }
}
