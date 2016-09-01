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
package org.opencord.cordvtn.api;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of a service network.
 */
public final class ServiceNetwork {

    public enum ServiceNetworkType {
        PRIVATE,
        PUBLIC,
        MANAGEMENT_HOST,
        MANAGEMENT_LOCAL,
        VSG,
        ACCESS_AGENT
    }

    public enum DirectAccessType {
        BIDIRECTIONAL,
        UNIDIRECTIONAL
    }

    private final NetworkId id;
    private final ServiceNetworkType type;
    private final Map<NetworkId, DirectAccessType> providers;

    private ServiceNetwork(NetworkId id,
                           ServiceNetworkType type,
                           Map<NetworkId, DirectAccessType> providers) {
        this.id = id;
        this.type = type;
        this.providers = providers;
    }

    /**
     * Returns the network id of the service network.
     *
     * @return network id
     */
    public NetworkId id() {
        return id;
    }

    /**
     * Returns the type of the service network.
     *
     * @return service network type
     */
    public ServiceNetworkType type() {
        return type;
    }

    /**
     * Returns the provider networks of this service network if exists.
     *
     * @return provider networks
     */
    public Map<NetworkId, DirectAccessType> providers() {
        return providers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof ServiceNetwork) {
            ServiceNetwork that = (ServiceNetwork) obj;
            if (Objects.equals(id, that.id) &&
                    Objects.equals(type, that.type) &&
                    Objects.equals(providers, that.providers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, providers);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("networkId", id)
                .add("type", type)
                .add("providers", providers)
                .toString();
    }

    /**
     * Returns new service network builder instance.
     *
     * @return service network builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of the service network entities.
     */
    public static final class Builder {
        private NetworkId id;
        private ServiceNetworkType type;
        private Map<NetworkId, DirectAccessType> providers = Maps.newHashMap();

        private Builder() {
        }

        /**
         * Builds an immutable service network.
         *
         * @return service network instance
         */
        public ServiceNetwork build() {
            checkNotNull(id, "Service network id cannot be null");
            checkNotNull(type, "Service network type cannot be null");
            providers = providers == null ? ImmutableMap.of() : providers;

            return new ServiceNetwork(id, type, providers);
        }

        /**
         * Returns service network builder with the supplied network ID.
         *
         * @param id network id
         * @return service network builder
         */
        public Builder id(NetworkId id) {
            this.id = id;
            return this;
        }

        /**
         * Returns service network builder with the supplied service network type.
         *
         * @param type service network type
         * @return service network builder
         */
        public Builder type(ServiceNetworkType type) {
            this.type = type;
            return this;
        }

        /**
         * Returns service network builder with the supplied provider service networks.
         *
         * @param providers provider service networks
         * @return service network builder
         */
        public Builder providers(Map<NetworkId, DirectAccessType> providers) {
            this.providers = providers;
            return this;
        }

        /**
         * Returns service network builder with the given additional provider network.
         *
         * @param id provider network id
         * @param type direct access type to the provider network
         * @return service network builder
         */
        public Builder addProvider(NetworkId id, DirectAccessType type) {
            checkNotNull(id, "Provider network ID cannot be null");
            checkNotNull(type, "Provider network type cannot be null");

            this.providers.put(id, type);
            return this;
        }
    }
}
