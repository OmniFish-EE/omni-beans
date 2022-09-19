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
package org.omnifaces.omnibeans.test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 *
 * @author arjan
 *
 */
@Entity
public class MyEntity {

    @Id
    private int id;
    private String name;

    /**
     * Default ctor needed by Jakarta Persistence
     */
    public MyEntity() {}

    /**
     * Convenience ctor
     *
     * @param id the id
     * @param name the name
     */
    public MyEntity(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     *
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     *
     * @param id
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }



}
