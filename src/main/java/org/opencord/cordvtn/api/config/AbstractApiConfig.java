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

import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * Representation of external API access configuration.
 */
public abstract class AbstractApiConfig {

    protected final String endpoint;
    protected final String user;
    protected final String password;

    /**
     * Default constructor.
     *
     * @param endpoint api endpoint
     * @param user     user name
     * @param password password of the user
     */
    protected AbstractApiConfig(String endpoint, String user, String password) {
        this.endpoint = endpoint;
        this.user = user;
        this.password = password;
    }

    /**
     * Returns the endpoint.
     *
     * @return endpoint
     */
    public String endpoint() {
        return endpoint;
    }

    /**
     * Returns the user.
     *
     * @return user
     */
    public String user() {
        return user;
    }

    /**
     * Returns the password.
     *
     * @return password
     */
    public String password() {
        return password;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, user, password);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj instanceof AbstractApiConfig)) {
            AbstractApiConfig that = (AbstractApiConfig) obj;
            if (Objects.equals(endpoint, that.endpoint) &&
                    Objects.equals(user, that.user) &&
                    Objects.equals(password, that.password)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("endpoint", endpoint)
                .add("user", user)
                .add("password", password)
                .toString();
    }
}
