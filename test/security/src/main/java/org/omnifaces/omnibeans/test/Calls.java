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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Makes calls for the probes.
 */
public final class Calls {

    private Calls() {
    }

    /**
     * Makes a call.
     *
     * @param call the call
     * @return what it returned, or the simple name of the exception it failed with (the cause, for a future)
     */
    public static String call(Callable<?> call) {
        try {
            return String.valueOf(call.call());
        } catch (ExecutionException e) {
            return e.getCause().getClass().getSimpleName();
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }
}
