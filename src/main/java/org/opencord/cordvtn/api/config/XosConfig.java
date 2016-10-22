/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencord.cordvtn.api.config;

/**
 * Representation of XOS API access configuration.
 */
public final class XosConfig extends AbstractApiConfig {

    /**
     * Default constructor.
     *
     * @param endpoint api endpoint
     * @param user     user name
     * @param password password of the user
     */
    public XosConfig(String endpoint, String user, String password) {
        super(endpoint, user, password);
    }
}
