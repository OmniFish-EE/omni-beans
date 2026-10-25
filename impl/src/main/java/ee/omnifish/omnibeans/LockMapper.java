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

import jakarta.ejb.AccessTimeout;
import jakarta.ejb.LockType;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;

import java.util.concurrent.TimeUnit;

import org.omnifaces.services.lock.Lock;
import org.omnifaces.services.lock.Lock.TimeoutType;

/**
 * Maps the locks of singleton session beans to OmniServices locks. Used by {@link OmniBeansExtension}.
 */
final class LockMapper {

    private LockMapper() {
    }

    /**
     * Maps the class' lock and those of its methods to OmniServices {@link Lock}. The default for a class is a write
     * lock, a method without a lock or access timeout of its own has the class' lock.
     */
    static void mapLocks(ClassConfig classConfig) {
        ClassInfo classInfo = classConfig.info();
        AnnotationInfo classLock = classInfo.annotation(jakarta.ejb.Lock.class);
        AnnotationInfo classAccessTimeout = classInfo.annotation(AccessTimeout.class);

        if (!classInfo.hasAnnotation(Lock.class)) {
            classConfig.addAnnotation(toLock(classLock, classAccessTimeout));
        }

        for (MethodConfig method : classConfig.methods()) {
            AnnotationInfo methodLock = method.info().annotation(jakarta.ejb.Lock.class);
            AnnotationInfo methodAccessTimeout = method.info().annotation(AccessTimeout.class);

            if ((methodLock != null || methodAccessTimeout != null) && !method.info().hasAnnotation(Lock.class)) {
                method.addAnnotation(toLock(
                    methodLock != null ? methodLock : classLock,
                    methodAccessTimeout != null ? methodAccessTimeout : classAccessTimeout));
            }
        }
    }

    private static Lock toLock(AnnotationInfo lock, AnnotationInfo accessTimeout) {
        Lock.Type type = lock == null || LockType.valueOf(lock.value().asEnumConstant()) == LockType.WRITE ? Lock.Type.WRITE : Lock.Type.READ;

        if (accessTimeout == null) {
            Lock defaults = Lock.Literal.INSTANCE;
            return Lock.Literal.of(type, defaults.timeoutType(), defaults.accessTimeout(), defaults.unit());
        }

        long timeout = accessTimeout.value().asLong();
        TimeUnit unit = TimeUnit.valueOf(accessTimeout.member("unit").asEnumConstant());

        // As AccessTimeout: -1 waits indefinitely, 0 doesn't wait
        TimeoutType timeoutType = timeout > 0 ? TimeoutType.TIMEOUT : timeout == 0 ? TimeoutType.NOT_PERMITTED : TimeoutType.INDEFINITTE;

        return Lock.Literal.of(type, timeoutType, timeout, unit);
    }

}
