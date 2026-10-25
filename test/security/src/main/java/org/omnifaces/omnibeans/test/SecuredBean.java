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

import jakarta.annotation.Resource;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;

/**
 * A session bean whose methods are open, for role {@code manager}, or for nobody; with overloads that are secured
 * differently. It tells who its caller is, and whether that is in role {@code auditor}, which only another bean uses
 * ({@link AuditBean}), and in role {@code manager}.
 */
@Stateless
public class SecuredBean {

    @Resource
    private SessionContext sessionContext;

    /**
     * Returns "open", to everybody.
     *
     * @return "open"
     */
    @PermitAll
    public String open() {
        return "open";
    }

    /**
     * Returns "managers", to managers.
     *
     * @return "managers"
     */
    @RolesAllowed("manager")
    public String managers() {
        return "managers";
    }

    /**
     * Returns "nobody", to nobody.
     *
     * @return "nobody"
     */
    @DenyAll
    public String nobody() {
        return "nobody";
    }

    /**
     * Returns "int", to managers.
     *
     * @param value ignored
     * @return "int"
     */
    @RolesAllowed("manager")
    public String overloaded(int value) {
        return "int";
    }

    /**
     * Returns "String", to everybody.
     *
     * @param value ignored
     * @return "String"
     */
    @PermitAll
    public String overloaded(String value) {
        return "String";
    }

    /**
     * Returns the caller, and whether it is in role auditor and in role manager.
     *
     * @return the caller and its roles
     */
    @PermitAll
    public String whoAmI() {
        return sessionContext.getCallerPrincipal().getName() + "/" + sessionContext.isCallerInRole("auditor") + "/" + sessionContext.isCallerInRole("manager");
    }
}
