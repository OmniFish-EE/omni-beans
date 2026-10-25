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

import jakarta.annotation.Priority;
import jakarta.ejb.EJBAccessException;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.io.Serializable;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Secures a session bean's business methods as an Enterprise Beans container does, through Jakarta Authorization: the
 * container's policy decides whether the caller may call the method (its {@code EJBMethodPermission}, which OmniBeans
 * put into service at startup with the bean's other statements), and the call then runs in the session beans' policy
 * context.
 * <p>
 * Runs after the pooled instance is taken, so once per call, and before the lock, the transaction and the bean's
 * interceptors. A refused call fails with an {@link EJBAccessException}, which isn't logged and doesn't discard the
 * instance.
 *
 * @serial exclude
 */
@Interceptor
@SessionBeanSecurity
@Priority(PLATFORM_BEFORE + 55)
public class SessionBeanSecurityInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    @Inject
    private SecurityDescriptorRegistry registry;

    /**
     * Authorizes the call, and makes it in the session beans' policy context.
     *
     * @param ctx the invocation context
     * @return what the method returned
     * @throws Exception what the method threw, or an {@link EJBAccessException} if the caller may not call it
     */
    @AroundInvoke
    public Object authorize(InvocationContext ctx) throws Exception {
        SecurityDescriptor securityDescriptor = registry.get(interceptedBean.getBeanClass());
        if (securityDescriptor == null) {
            throw new EJBAccessException(
                "Method " + ctx.getMethod().getName() + " of session bean " + interceptedBean.getBeanClass().getName()
                + " can't be authorized: the build found no security for it");
        }

        String contextId = JakartaAuthorization.authorize(securityDescriptor, ctx.getMethod());
        if (contextId == null) {
            return ctx.proceed();
        }

        return JakartaAuthorization.callInContext(contextId, ctx::proceed);
    }
}
