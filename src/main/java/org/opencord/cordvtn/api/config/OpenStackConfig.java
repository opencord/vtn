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
 * Representation of OpenStack API access configuration.
 */
public final class OpenStackConfig extends AbstractApiConfig {

    private final String tenant;

    /**
     * Default constructor.
     *
     * @param endpoint api endpoint
     * @param tenant   tenant name
     * @param user     user name
     * @param password password of the user
     */
    public OpenStackConfig(String endpoint, String tenant, String user,
                           String password) {
        super(endpoint, user, password);
        this.tenant = tenant;
    }

    /**
     * Returns the tenant name.
     *
     * @return tenant name
     */
    public String tenant() {
        return tenant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, tenant, user, password);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj instanceof OpenStackConfig)) {
            OpenStackConfig that = (OpenStackConfig) obj;
            if (Objects.equals(endpoint, that.endpoint) &&
                    Objects.equals(tenant, that.tenant) &&
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
                .add("tenant", tenant)
                .add("user", user)
                .add("password", password)
                .toString();
    }
}
