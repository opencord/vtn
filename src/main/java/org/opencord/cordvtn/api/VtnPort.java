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
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.openstack4j.model.network.Port;

import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of the port containing all port information consumed by VTN service.
 */
public final class VtnPort extends ServicePort {

    private static final String ERR_IP_MISSING = "VTN port IP adderess is missing";

    private final NetworkId netId;
    private final MacAddress mac;
    private final IpAddress ip;

    private VtnPort(PortId id,
                    NetworkId netId,
                    MacAddress mac,
                    IpAddress ip,
                    VlanId vlanId,
                    Set<AddressPair> addressPairs) {
        super(id, vlanId, addressPairs);
        this.netId = netId;
        this.mac = mac;
        this.ip = ip;
    }

    /**
     * Returns the network ID of this port.
     *
     * @return network id
     */
    public NetworkId netId() {
        return netId;
    }

    /**
     * Returns the MAC address of this port.
     *
     * @return mac address
     */
    public MacAddress mac() {
        return mac;
    }

    /**
     * Returns the IP address of this port.
     *
     * @return ip address
     */
    public IpAddress ip() {
        return ip;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof VtnPort) {
            VtnPort that = (VtnPort) obj;
            if (Objects.equals(id, that.id) &&
                    Objects.equals(netId, that.netId) &&
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
        return Objects.hash(id, netId, mac, ip, vlanId, addressPairs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("id", id)
                .add("netId", netId)
                .add("mac", mac)
                .add("ip", ip)
                .add("vlanId", vlanId)
                .add("addressPairs", addressPairs)
                .toString();
    }

    /**
     * Returns the immutable VTN port with the supplied Neutron port with additional
     * vtn port information.
     *
     * @param port        neutron port
     * @param servicePort vtn port
     * @return vtn port
     */
    public static VtnPort of(Port port, ServicePort servicePort) {
        validateNeutronPort(port);
        if (servicePort != null) {
            checkArgument(Objects.equals(port.getId(), servicePort.id().id()));
        }

        return builder().id(PortId.of(port.getId()))
                .netId(NetworkId.of(port.getNetworkId()))
                .mac(MacAddress.valueOf(port.getMacAddress()))
                .ip(IpAddress.valueOf(port.getFixedIps().iterator().next().getIpAddress()))
                .vlanId(servicePort == null ? null : servicePort.vlanId().orElse(null))
                .addressPairs(servicePort == null ? ImmutableSet.of() : servicePort.addressPairs())
                .build();
    }

    private static void validateNeutronPort(Port port) {
        checkNotNull(port);
        checkArgument(port.getFixedIps().size() > 0, ERR_IP_MISSING);
    }

    /**
     * Returns the immutable VTN port with the supplied VTN port with additional
     * vtn port information.
     *
     * @param vtnPort     vtn port
     * @param servicePort vtn port
     * @return vtn port
     */
    public static VtnPort of(VtnPort vtnPort, ServicePort servicePort) {
        return builder(vtnPort)
                .vlanId(servicePort.vlanId().orElse(null))
                .addressPairs(servicePort.addressPairs())
                .build();
    }

    /**
     * Returns new vtn port builder instance.
     *
     * @return vtn port builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns new vtn port builder instance with copy of the supplied vtn port.
     *
     * @param vtnPort vtn port
     * @return vtn port builder
     */
    public static Builder builder(VtnPort vtnPort) {
        return new Builder().id(vtnPort.id())
                .netId(vtnPort.netId())
                .mac(vtnPort.mac())
                .ip(vtnPort.ip())
                .vlanId(vtnPort.vlanId().orElse(null))
                .addressPairs(vtnPort.addressPairs());
    }

    /**
     * Builder of the vtn port entities.
     */
    public static final class Builder {
        private PortId id;
        private NetworkId netId;
        private MacAddress mac;
        private IpAddress ip;
        private VlanId vlanId;
        private Set<AddressPair> addressPairs = ImmutableSet.of();

        private Builder() {
        }

        /**
         * Builds an immutable vtn port.
         *
         * @return vtn port instance
         */
        public VtnPort build() {
            checkNotNull(id, "VtnPort port id cannot be null");
            checkNotNull(netId, "VtnPort network id cannot be null");
            checkNotNull(mac, "VtnPort mac address cannot be null");
            checkNotNull(ip, "VtnPort ip address cannot be null");
            addressPairs = addressPairs == null ? ImmutableSet.of() : addressPairs;

            return new VtnPort(id, netId, mac, ip, vlanId, addressPairs);
        }

        /**
         * Returns vtn port builder with the supplied port id.
         *
         * @param id port id
         * @return vtn port builder
         */
        public Builder id(PortId id) {
            this.id = id;
            return this;
        }

        /**
         * Returns vtn port builder with the supplied network id.
         *
         * @param netId network id
         * @return vtn port builder
         */
        public Builder netId(NetworkId netId) {
            this.netId = netId;
            return this;
        }

        /**
         * Returns vtn port builder with the supplied mac address.
         *
         * @param mac mac address
         * @return vtn port builder
         */
        public Builder mac(MacAddress mac) {
            this.mac = mac;
            return this;
        }

        /**
         * Returns vtn port builder with the supplied ip address.
         *
         * @param ip ip address
         * @return vtn port builder
         */
        public Builder ip(IpAddress ip) {
            this.ip = ip;
            return this;
        }

        /**
         * Returns vtn port builder with the supplied VLAN ID.
         *
         * @param vlanId vlan id
         * @return vtn port builder
         */
        public Builder vlanId(VlanId vlanId) {
            this.vlanId = vlanId;
            return this;
        }

        /**
         * Returns vtn port builder with the supplied address pairs.
         *
         * @param addressPairs set of address pairs
         * @return vtn port builder
         */
        public Builder addressPairs(Set<AddressPair> addressPairs) {
            this.addressPairs = addressPairs;
            return this;
        }

        /**
         * Returns vtn port builder with the given additional address pair.
         *
         * @param addressPair address pair to add
         * @return vtn port builder
         */
        public Builder addAddressPair(AddressPair addressPair) {
            checkNotNull(addressPair, "VtnPort address pair cannot be null");

            Set<AddressPair> updated = Sets.newHashSet(this.addressPairs);
            updated.add(addressPair);
            this.addressPairs = ImmutableSet.copyOf(updated);
            return this;
        }
    }
}
