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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.onlab.packet.VlanId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of a service port.
 */
public final class ServicePort {

    private final PortId id;
    private final VlanId vlanId;
    private final Set<AddressPair> addressPairs;

    private ServicePort(PortId id,
                        VlanId vlanId,
                        Set<AddressPair> addressPairs) {
        this.id = id;
        this.vlanId = vlanId;
        this.addressPairs = addressPairs;
    }

    /**
     * Returns the port id of the service port.
     *
     * @return port id
     */
    public PortId id() {
        return id;
    }

    /**
     * Returns the vlan id of the the service port if exists.
     *
     * @return vlan id
     */
    public Optional<VlanId> vlanId() {
        return Optional.ofNullable(vlanId);
    }

    /**
     * Returns the additional address pairs used in this port.
     *
     * @return set of ip and mac address pairs
     */
    public Set<AddressPair> addressPairs() {
        return addressPairs;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof ServicePort) {
            ServicePort that = (ServicePort) obj;
            if (Objects.equals(id, that.id) &&
                    Objects.equals(vlanId, that.vlanId) &&
                    Objects.equals(addressPairs, that.addressPairs)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, vlanId, addressPairs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("portId", id)
                .add("vlanId", vlanId)
                .add("addressPairs", addressPairs)
                .toString();
    }

    /**
     * Returns new service port builder instance.
     *
     * @return service port builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of the service port entities.
     */
    public static final class Builder {
        private PortId id;
        private VlanId vlanId;
        private Set<AddressPair> addressPairs = Sets.newHashSet();

        private Builder() {
        }

        /**
         * Builds an immutable service port.
         *
         * @return service port instance
         */
        public ServicePort build() {
            checkNotNull(id, "ServicePort port id cannot be null");
            addressPairs = addressPairs == null ? ImmutableSet.of() : addressPairs;

            return new ServicePort(id, vlanId, addressPairs);
        }

        /**
         * Returns service port builder with the supplied port port id.
         *
         * @param id port id
         * @return service port builder
         */
        public Builder id(PortId id) {
            this.id = id;
            return this;
        }

        /**
         * Returns service port builder with the supplied VLAN ID.
         *
         * @param vlanId vlan id
         * @return service port builder
         */
        public Builder vlanId(VlanId vlanId) {
            this.vlanId = vlanId;
            return this;
        }

        /**
         * Returns service port builder with the supplied address pairs.
         *
         * @param addressPairs set of address pairs
         * @return service port builder
         */
        public Builder addressPairs(Set<AddressPair> addressPairs) {
            this.addressPairs = addressPairs;
            return this;
        }

        /**
         * Returns service port builder with the given additional address pair.
         *
         * @param addressPair address pair to add
         * @return service port builder
         */
        public Builder addAddressPair(AddressPair addressPair) {
            checkNotNull(addressPair, "ServicePort address pair cannot be null");

            this.addressPairs.add(addressPair);
            return this;
        }
    }
}
