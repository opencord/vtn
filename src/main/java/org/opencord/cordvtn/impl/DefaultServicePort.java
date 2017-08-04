/*
 * Copyright 2017-present Open Networking Foundation
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
package org.opencord.cordvtn.impl;

import com.google.common.base.MoreObjects;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.opencord.cordvtn.api.net.AddressPair;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServicePort;

import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.EMPTY_SET;

/**
 * Implementation of {@link ServicePort}.
 */
public final class DefaultServicePort implements ServicePort {

    private static final String ERR_ID_MISSING = "Service port ID is missing";

    private final PortId id;
    private final String name;
    private final NetworkId networkId;
    private final MacAddress mac;
    private final IpAddress ip;
    private final VlanId vlanId;
    private final Set<AddressPair> addressPairs;

    private DefaultServicePort(PortId id,
                               String name,
                               NetworkId networkId,
                               MacAddress mac,
                               IpAddress ip,
                               VlanId vlanId,
                               Set<AddressPair> addressPairs) {
        this.id = id;
        this.name = name;
        this.networkId = networkId;
        this.mac = mac;
        this.ip = ip;
        this.vlanId = vlanId;
        this.addressPairs = addressPairs;
    }

    @Override
    public PortId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public NetworkId networkId() {
        return networkId;
    }

    @Override
    public MacAddress mac() {
        return mac;
    }

    @Override
    public IpAddress ip() {
        return ip;
    }

    @Override
    public VlanId vlanId() {
        return vlanId;
    }

    @Override
    public Set<AddressPair> addressPairs() {
        return addressPairs;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof DefaultServicePort) {
            DefaultServicePort that = (DefaultServicePort) obj;
            if (Objects.equals(id, that.id) &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(networkId, that.networkId) &&
                    Objects.equals(mac, that.mac) &&
                    Objects.equals(ip, that.ip) &&
                    Objects.equals(vlanId, that.vlanId) &&
                    Objects.equals(addressPairs, that.addressPairs)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, networkId, mac, ip, vlanId, addressPairs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("id", id)
                .add("name", name)
                .add("networkId", networkId)
                .add("mac", mac)
                .add("ip", ip)
                .add("vlanId", vlanId)
                .add("addressPairs", addressPairs)
                .toString();
    }

    /**
     * Returns new service port builder instance.
     *
     * @return vtn port builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns new builder instance with copy of the supplied service port.
     *
     * @param sport vtn port
     * @return vtn port builder
     */
    public static Builder builder(ServicePort sport) {
        return new Builder()
                .id(sport.id())
                .name(sport.name())
                .networkId(sport.networkId())
                .mac(sport.mac())
                .ip(sport.ip())
                .vlanId(sport.vlanId())
                .addressPairs(sport.addressPairs());
    }

    /**
     * Returns service port builder instance with updated values. Any value
     * not specified in the updated but in the existing remains in the updated
     * builder.
     *
     * @param existing existing service port
     * @param updated  updated service port
     * @return service network builder
     */
    public static Builder builder(ServicePort existing, ServicePort updated) {
        checkArgument(Objects.equals(existing.id(), updated.id()));
        // FIXME allow removing existing values
        return new Builder()
                .id(existing.id())
                .name(updated.name() != null ? updated.name() : existing.name())
                .networkId(updated.networkId() != null ?
                                   updated.networkId() : existing.networkId())
                .mac(updated.mac() != null ? updated.mac() : existing.mac())
                .ip(updated.ip() != null ? updated.ip() : existing.ip())
                .vlanId(updated.vlanId() != null ? updated.vlanId() : existing.vlanId())
                .addressPairs(updated.addressPairs() != EMPTY_SET ?
                                      updated.addressPairs() : existing.addressPairs());
    }

    public static final class Builder implements ServicePort.Builder {

        private PortId id;
        private String name;
        private NetworkId networkId;
        private MacAddress mac;
        private IpAddress ip;
        private VlanId vlanId;
        private Set<AddressPair> addressPairs;

        private Builder() {
        }

        @Override
        public ServicePort build() {
            checkNotNull(id, ERR_ID_MISSING);
            addressPairs = addressPairs != null ? addressPairs : EMPTY_SET;
            return new DefaultServicePort(
                    id, name,
                    networkId, mac, ip, vlanId,
                    addressPairs);
        }

        @Override
        public Builder id(PortId id) {
            this.id = id;
            return this;
        }

        @Override
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder networkId(NetworkId networkId) {
            this.networkId = networkId;
            return this;
        }

        @Override
        public Builder mac(MacAddress mac) {
            this.mac = mac;
            return this;
        }

        @Override
        public Builder ip(IpAddress ip) {
            this.ip = ip;
            return this;
        }

        @Override
        public Builder vlanId(VlanId vlanId) {
            this.vlanId = vlanId;
            return this;
        }

        @Override
        public Builder addressPairs(Set<AddressPair> addressPairs) {
            this.addressPairs = addressPairs;
            return this;
        }
    }
}
