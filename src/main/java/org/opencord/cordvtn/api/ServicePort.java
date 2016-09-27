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
import org.onlab.packet.VlanId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of a service port.
 */
public class ServicePort {

    private static final String ERR_ID = "Service port ID cannot be null";

    protected final PortId id;
    protected final VlanId vlanId;
    protected final Set<AddressPair> addressPairs;

    public ServicePort(PortId id,
                       VlanId vlanId,
                       Set<AddressPair> addressPairs) {
        this.id = checkNotNull(id, ERR_ID);
        this.vlanId = vlanId;
        this.addressPairs = addressPairs == null ? ImmutableSet.of() : addressPairs;
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
}
