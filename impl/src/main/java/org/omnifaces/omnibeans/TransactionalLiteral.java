/*
 * Copyright 2022 OmniFaces
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
package org.omnifaces.omnibeans;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.transaction.Transactional;

/**
 * Supports inline instantiation of the Transactional annotation.
 *
 */
public class TransactionalLiteral extends AnnotationLiteral<Transactional> implements Transactional {

    private static final long serialVersionUID = 1L;

    private final TxType value;
    private final Class[] rollbackOn;
    private final Class[] dontRollbackOn;

    /**
     * Default instance of the {@link Transactional} annotation.
     */
    public static final TransactionalLiteral INSTANCE = of(
            TxType.REQUIRED,
            new Class[] {},
            new Class[] {}
    );

    public static final TransactionalLiteral of(TxType value) {
        return of(value, new Class[] {}, new Class[] {});
    }

    public static final TransactionalLiteral of(TxType value, Class[] rollbackOn, Class[] dontRollbackOn) {
        return new TransactionalLiteral(value, rollbackOn, dontRollbackOn);
    }

    private TransactionalLiteral(TxType value, Class[] rollbackOn, Class[] dontRollbackOn) {
        this.value = value;
        this.rollbackOn = rollbackOn;
        this.dontRollbackOn = dontRollbackOn;
    }

    @Override
    public TxType value() {
        return value;
    }

    @Override
    public Class[] rollbackOn() {
        return rollbackOn;
    }

    @Override
    public Class[] dontRollbackOn() {
        return dontRollbackOn;
    }


}
