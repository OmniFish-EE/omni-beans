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
package ee.omnifish.omnibeans;

import java.security.Principal;
import java.util.Map;

import jakarta.ejb.EJBHome;
import jakarta.ejb.EJBLocalHome;
import jakarta.ejb.EJBLocalObject;
import jakarta.ejb.EJBObject;
import jakarta.ejb.SessionContext;
import jakarta.ejb.TimerService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.UserTransaction;

@ApplicationScoped
public class SessionContextImpl implements SessionContext {

    @Override
    public Principal getCallerPrincipal() throws IllegalStateException {
        return null;
    }

    @Override
    public boolean isCallerInRole(String roleName) throws IllegalStateException {
        return false;
    }

    @Override
    public UserTransaction getUserTransaction() throws IllegalStateException {
        return null;
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException {

    }

    @Override
    public boolean getRollbackOnly() throws IllegalStateException {
        return false;
    }

    @Override
    public TimerService getTimerService() throws IllegalStateException {
        return null;
    }

    @Override
    public Object lookup(String name) throws IllegalArgumentException {
        return null;
    }

    @Override
    public Map<String, Object> getContextData() {
        return null;
    }

    @Override
    public <T> T getBusinessObject(Class<T> businessInterface) throws IllegalStateException {
        return null;
    }

    @Override
    public Class getInvokedBusinessInterface() throws IllegalStateException {
        return null;
    }

    @Override
    public boolean wasCancelCalled() throws IllegalStateException {
        return false;
    }

    @Override
    public EJBHome getEJBHome() throws IllegalStateException {
        return null;
    }

    @Override
    public EJBLocalHome getEJBLocalHome() throws IllegalStateException {
        return null;
    }

    @Override
    public EJBLocalObject getEJBLocalObject() throws IllegalStateException {
        return null;
    }

    @Override
    public EJBObject getEJBObject() throws IllegalStateException {
        return null;
    }

}
