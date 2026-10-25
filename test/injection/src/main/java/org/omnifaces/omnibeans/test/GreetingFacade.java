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
import jakarta.ejb.EJB;
import jakarta.ejb.EJBContext;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;

/**
 * A session bean with {@code @EJB} fields and a setter, with and without a bean name, and with a {@code @Resource}
 * field and setter for its contexts.
 */
@Stateless
public class GreetingFacade {

    @EJB(beanName = "english")
    private Greeter english;

    private Greeter dutch;

    @EJB
    private Counter counter;

    @Resource
    private EJBContext ejbContext;

    private SessionContext sessionContext;

    /**
     * Creates the bean.
     */
    public GreetingFacade() {
    }

    /**
     * Sets the Dutch greeter.
     *
     * @param dutch the greeter
     */
    @EJB(beanName = "DutchGreeter")
    public void setDutch(Greeter dutch) {
        this.dutch = dutch;
    }

    /**
     * Sets the session context.
     *
     * @param sessionContext the session context
     */
    @Resource
    public void setSessionContext(SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    /**
     * Greets with both greeters, and counts.
     *
     * @return the greetings and the count
     */
    public String greetings() {
        return english.greet() + "," + dutch.greet() + "," + counter.next();
    }

    /**
     * Tells whether the contexts were injected, by asking each whether the transaction the method runs in will roll back,
     * who the caller is, and whether the caller is in role {@code guest}.
     *
     * @return the answers of the session context and the bean context
     */
    public String contexts() {
        return answer(sessionContext) + "," + answer(ejbContext);
    }

    private static String answer(EJBContext context) {
        if (context == null) {
            return "not injected";
        }

        return context.getRollbackOnly() + "/" + context.getCallerPrincipal().getName() + "/" + context.isCallerInRole("guest");
    }
}
