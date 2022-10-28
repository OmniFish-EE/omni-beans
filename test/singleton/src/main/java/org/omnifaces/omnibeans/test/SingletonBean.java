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

import static jakarta.ejb.LockType.READ;
import static jakarta.ejb.LockType.WRITE;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Lock;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;

@Startup
@Singleton
public class SingletonBean {
    private StringBuilder builder;

    @PostConstruct
    private void postConstruct() {
        builder = new StringBuilder();
    }

    @Lock(WRITE)
    public void addValue(String value) {
        builder.append(value);
    }

    @Lock(READ)
    public String getAccumulatedValues() {
        return builder.toString();
    }
}