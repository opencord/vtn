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
     * service port information.
     *
     * @param port        neutron port
     * @param servicePort service port
     * @return vtn port
     */
    public static VtnPort of(Port port, ServicePort servicePort) {
        validateNeutronPort(port);
        if (servicePort != null) {
            checkArgument(Objects.equals(port.getId(), servicePort.id().id()));
        }

        return new VtnPort(
                PortId.of(port.getId()),
                NetworkId.of(port.getNetworkId()),
                MacAddress.valueOf(port.getMacAddress()),
                IpAddress.valueOf(port.getFixedIps().iterator().next().getIpAddress()),
                servicePort == null ? null : servicePort.vlanId().orElse(null),
                servicePort == null ? ImmutableSet.of() : servicePort.addressPairs()
        );
    }

    private static void validateNeutronPort(Port port) {
        checkNotNull(port);
        checkArgument(port.getFixedIps().size() > 1, ERR_IP_MISSING);
    }
}
