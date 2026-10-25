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

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * A stateless bean that saves and loads {@link MyEntity} instances, in the default (required) transaction.
 */
@Stateless
public class MyEntityService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates the bean.
     */
    public MyEntityService() {
    }

    /**
     * Saves an entity.
     *
     * @param entity the entity to save
     */
    public void save(MyEntity entity ) {
        entityManager.persist(entity);
    }

    /**
     * Loads an entity.
     *
     * @param id the id of the entity
     * @return the entity, or null if there is none
     */
    public MyEntity load(int id) {
        return entityManager.find(MyEntity.class, id);
    }

}
