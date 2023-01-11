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
/**
 * @author Arjan Tijms
 */
module ee.omnifish.omnibeans {

    exports ee.omnifish.omnibeans;
    opens ee.omnifish.omnibeans;
    requires jakarta.ejb;
    requires jakarta.cdi;
    requires org.omnifaces.services;
    requires jakarta.security;

}

