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
package org.opencord.cordvtn.api.net;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opencord.cordvtn.api.dependency.Dependency.Type.BIDIRECTIONAL;

/**
 * Representation of a service network which holds service specific information,
 * like service type or dependency, in addition to the common network.
 */
public class ServiceNetwork {

    private static final String ERR_ID = "Service network ID cannot be null";
    private static final String ERR_TYPE = "Service network type cannot be null";

    public enum ServiceNetworkType {
        PRIVATE,
        PUBLIC,
        MANAGEMENT_HOST,
        MANAGEMENT_LOCAL,
        VSG,
        ACCESS_AGENT
    }

    protected final NetworkId id;
    protected final ServiceNetworkType type;
    protected final Set<ProviderNetwork> providers;

    public ServiceNetwork(NetworkId id,
                          ServiceNetworkType type,
                          Set<ProviderNetwork> providers) {
        this.id = checkNotNull(id, ERR_ID);
        this.type = checkNotNull(type, ERR_TYPE);
        this.providers = providers == null ? ImmutableSet.of() : providers;
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
    public Set<ProviderNetwork> providers() {
        return providers;
    }

    /**
     * Returns if the given network is the provider of this network or not.
     *
     * @param netId network id
     * @return true if the given network is the provider of this network
     */
    public boolean isProvider(NetworkId netId) {
        return providers.stream().filter(p -> Objects.equals(p.id(), netId))
                .findAny().isPresent();
    }

    /**
     * Returns if the given network is the provider of this network with
     * bidirectional access type.
     *
     * @param netId network id
     * @return true if the given network is a bidrectional provider
     */
    public boolean isBidirectionalProvider(NetworkId netId) {
        return providers.stream().filter(p -> Objects.equals(p.id(), netId))
                .filter(p -> p.type() == BIDIRECTIONAL)
                .findAny().isPresent();
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
}
