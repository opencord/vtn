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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Subnet;

import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType.PRIVATE;

/**
 * Representation of the network containing all network information consumed by
 * VTN service.
 */
public final class VtnNetwork extends ServiceNetwork {

    private static final String ERR_SEGMENT_ID_MISSING = "VTN network segment ID is missing";
    private static final String ERR_GATEWAY_IP_MISSING = "VTN subnet gateway IP is missing";

    private final SegmentId segmentId;
    private final IpPrefix subnet;
    private final IpAddress serviceIp;

    private VtnNetwork(NetworkId id,
                       SegmentId segmentId,
                       IpPrefix subnet,
                       IpAddress serviceIp,
                       ServiceNetworkType type,
                       Map<NetworkId, DirectAccessType> providers) {
        super(id, type, providers);
        this.segmentId = segmentId;
        this.subnet = subnet;
        this.serviceIp = serviceIp;
    }

    /**
     * Returns the network ID.
     *
     * @return network id
     */
    public NetworkId id() {
        return id;
    }

    /**
     * Returns the segment ID of this network.
     *
     * @return segment id
     */
    public SegmentId segmentId() {
        return segmentId;
    }

    /**
     * Returns the subnet used in this network.
     *
     * @return subnet
     */
    public IpPrefix subnet() {
        return subnet;
    }

    /**
     * Returns the service IP address of this network.
     *
     * @return ip address
     */
    public IpAddress serviceIp() {
        return serviceIp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof VtnNetwork) {
            VtnNetwork that = (VtnNetwork) obj;
            if (Objects.equals(id, that.id) &&
                    Objects.equals(segmentId, that.segmentId) &&
                    Objects.equals(subnet, that.subnet) &&
                    Objects.equals(serviceIp, that.serviceIp) &&
                    Objects.equals(type, that.type) &&
                    Objects.equals(providers, that.providers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, segmentId, subnet, serviceIp, type, providers);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("id", id)
                .add("segmentId", segmentId)
                .add("subnet", subnet)
                .add("serviceIp", serviceIp)
                .add("type", type)
                .add("providers", providers)
                .toString();
    }

    /**
     * Returns immutable VTN network with the supplied Neutron network, subnet,
     * and additional service network information.
     *
     * @param network    neutron network
     * @param subnet     neutron subnet
     * @param serviceNet service network
     * @return vtn network
     */
    public static VtnNetwork of(Network network, Subnet subnet, ServiceNetwork serviceNet) {
        validateNeutronNetwork(network, subnet);
        if (serviceNet != null) {
            checkArgument(Objects.equals(network.getId(), serviceNet.id().id()));
        }
        return new VtnNetwork(
                NetworkId.of(network.getId()),
                SegmentId.of(Long.valueOf(network.getProviderSegID())),
                IpPrefix.valueOf(subnet.getCidr()),
                IpAddress.valueOf(subnet.getGateway()),
                serviceNet == null ? PRIVATE : serviceNet.type(),
                serviceNet == null ? ImmutableMap.of() : serviceNet.providers()
        );
    }

    /**
     * Returns immutable VTN network with the supplied VTN network and service network
     * information.
     *
     * @param vtnNet     vtn network
     * @param serviceNet service network
     * @return vtn network
     */
    public static VtnNetwork of(VtnNetwork vtnNet, ServiceNetwork serviceNet) {
        checkNotNull(vtnNet);
        return new VtnNetwork(
                vtnNet.id(),
                vtnNet.segmentId(),
                vtnNet.subnet(),
                vtnNet.serviceIp(),
                serviceNet == null ? PRIVATE : serviceNet.type(),
                serviceNet == null ? ImmutableMap.of() : serviceNet.providers()
        );
    }

    private static void validateNeutronNetwork(Network network, Subnet subnet) {
        checkNotNull(network);
        checkNotNull(subnet);
        checkArgument(Objects.equals(network.getId(), subnet.getNetworkId()));
        checkArgument(!Strings.isNullOrEmpty(network.getProviderSegID()), ERR_SEGMENT_ID_MISSING);
        checkArgument(!Strings.isNullOrEmpty(subnet.getGateway()), ERR_GATEWAY_IP_MISSING);
    }
}
