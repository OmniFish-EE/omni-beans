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
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.EJB;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;

/**
 * Runs as role {@code runner}: it sees its caller itself, the bean it calls sees the role.
 */
@Stateless
@RunAs("runner")
@PermitAll
public class RunAsBean {

    @Resource
    private SessionContext sessionContext;

    @EJB
    private UnsecuredBean unsecuredBean;

    /**
     * Returns its own caller, followed by what the bean it calls sees.
     *
     * @return the callers
     */
    public String chain() {
        return sessionContext.getCallerPrincipal().getName() + ">" + unsecuredBean.who();
    }

    /**
     * Fails, in the bean it calls.
     */
    public void failing() {
        unsecuredBean.fail();
    }
}
