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
package ee.omnifish.omnibeans;

import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionManagement;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import ee.omnifish.omnibeans.transactions.BeanManagedTransactions;
import ee.omnifish.omnibeans.transactions.BeanManagedTransactionsInterceptor;
import ee.omnifish.omnibeans.transactions.TransactionalLiteral;

import static ee.omnifish.omnibeans.Utils.isEnumMember;

/**
 * Maps Container Managed Transactions and Bean Managed Transactions to Jakarta Transactions' {@code @Transactional}.
 * Used by {@link OmniBeansExtension}.
 */
final class TransactionMapper {

    private TransactionMapper() {
    }

    /**
     * Maps Container Managed Transactions to {@link Transactional}, with the class' and the methods' transaction
     * attributes. A bean with Bean Managed Transactions doesn't run in the caller's transaction, and can use a user
     * transaction, as with {@link TxType#NOT_SUPPORTED}; the {@link BeanManagedTransactionsInterceptor} rolls back a
     * transaction it leaves open.
     */
    static void mapTransactions(ClassConfig classConfig) {
        ClassInfo classInfo = classConfig.info();

        if (isEnumMember(classInfo.annotation(TransactionManagement.class), "BEAN")) {
            if (!classInfo.hasAnnotation(Transactional.class)) {
                classConfig.addAnnotation(TransactionalLiteral.of(TxType.NOT_SUPPORTED));
            }

            classConfig.addAnnotation(BeanManagedTransactions.Literal.INSTANCE);

            return;
        }

        if (!classInfo.hasAnnotation(Transactional.class)) {
            AnnotationInfo transactionAttribute = classInfo.annotation(TransactionAttribute.class);
            classConfig.addAnnotation(transactionAttribute == null ? TransactionalLiteral.INSTANCE : toTransactional(transactionAttribute));
        }

        for (MethodConfig method : classConfig.methods()) {
            mapTransactionAttribute(method);
        }
    }

    static void mapTransactionAttribute(MethodConfig method) {
        AnnotationInfo transactionAttribute = method.info().annotation(TransactionAttribute.class);

        if (transactionAttribute != null && !method.info().hasAnnotation(Transactional.class)) {
            method.addAnnotation(toTransactional(transactionAttribute));
        }
    }

    static Transactional toTransactional(AnnotationInfo transactionAttribute) {
        // The names of TransactionAttributeType and TxType are the same
        return TransactionalLiteral.of(TxType.valueOf(transactionAttribute.value().asEnumConstant()));
    }


}
