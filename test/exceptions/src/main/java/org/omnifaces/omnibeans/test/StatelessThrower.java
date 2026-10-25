/*
 * Copyright 2022 OmniFish
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

import java.util.concurrent.Future;

import jakarta.annotation.PreDestroy;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.EJBException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * A stateless bean whose methods throw all kinds of exceptions.
 */
@Stateless
public class StatelessThrower {

    @Inject
    private Events events;

    /**
     * Creates the bean.
     */
    public StatelessThrower() {
    }

    /**
     * Records that this instance is destroyed.
     */
    @PreDestroy
    public void destroyed() {
        events.add("destroyed " + Events.id(this));
    }

    /**
     * Returns the identification of the instance the call ran on.
     *
     * @return the identification
     */
    public String instance() {
        return Events.id(this);
    }

    /**
     * Throws a system exception.
     */
    public void systemException() {
        throw new IllegalStateException("system");
    }

    /**
     * Throws an error, which is a system exception.
     */
    public void error() {
        throw new AssertionError("error");
    }

    /**
     * Throws an {@link EJBException}, which is a system exception.
     */
    public void ejbException() {
        throw new EJBException("bean");
    }

    /**
     * Throws a checked exception, which is an application exception.
     *
     * @throws ApplicationProblem always
     */
    public void applicationException() throws ApplicationProblem {
        throw new ApplicationProblem("checked");
    }

    /**
     * Throws an unchecked exception annotated as application exception.
     */
    public void applicationRuntimeException() {
        throw new ApplicationRuntimeProblem("unchecked");
    }

    /**
     * Throws an unchecked exception whose superclass is annotated as application exception.
     */
    public void inheritedApplicationRuntimeException() {
        throw new InheritedApplicationRuntimeProblem("inherited");
    }

    /**
     * Throws an unchecked exception whose superclass is annotated as application exception, but not for subclasses.
     */
    public void notInheritingSystemException() {
        throw new NotInheritingSystemProblem("not inherited");
    }

    /**
     * Throws a system exception, asynchronously.
     *
     * @return never
     */
    @Asynchronous
    public Future<String> asyncSystemException() {
        throw new IllegalStateException("async system");
    }

    /**
     * Throws an application exception, asynchronously.
     *
     * @return never
     * @throws ApplicationProblem always
     */
    @Asynchronous
    public Future<String> asyncApplicationException() throws ApplicationProblem {
        throw new ApplicationProblem("async checked");
    }
}
