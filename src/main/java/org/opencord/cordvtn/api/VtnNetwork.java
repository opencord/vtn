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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Subnet;

import java.util.Objects;
import java.util.Set;

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
                       Set<ProviderNetwork> providers) {
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

        return builder().id(NetworkId.of(network.getId()))
                .segmentId(SegmentId.of(Long.valueOf(network.getProviderSegID())))
                .subnet(IpPrefix.valueOf(subnet.getCidr()))
                .serviceIp(IpAddress.valueOf(subnet.getGateway()))
                .type(serviceNet == null ? PRIVATE : serviceNet.type())
                .providers(serviceNet == null ? ImmutableSet.of() : serviceNet.providers())
                .build();
    }

    private static void validateNeutronNetwork(Network network, Subnet subnet) {
        checkNotNull(network);
        checkNotNull(subnet);
        checkArgument(Objects.equals(network.getId(), subnet.getNetworkId()));
        checkArgument(!Strings.isNullOrEmpty(network.getProviderSegID()), ERR_SEGMENT_ID_MISSING);
        checkArgument(!Strings.isNullOrEmpty(subnet.getGateway()), ERR_GATEWAY_IP_MISSING);
    }

    /**
     * Returns new vtn network builder instance.
     *
     * @return vtn network builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns new vtn network builder instance with copy of the given vtn network.
     *
     * @param vtnNet vtn network
     * @return vtn network builder
     */
    public static Builder builder(VtnNetwork vtnNet) {
        return new Builder()
                .id(vtnNet.id())
                .segmentId(vtnNet.segmentId())
                .subnet(vtnNet.subnet())
                .serviceIp(vtnNet.serviceIp())
                .type(vtnNet.type())
                .providers(vtnNet.providers());
    }

    /**
     * Builder of the vtn network entities.
     */
    public static final class Builder {
        private NetworkId id;
        private SegmentId segmentId;
        private IpPrefix subnet;
        private IpAddress serviceIp;
        private ServiceNetworkType type;
        private Set<ProviderNetwork> providers = ImmutableSet.of();

        private Builder() {
        }

        /**
         * Builds an immutable vtn network.
         *
         * @return vtn network instance
         */
        public VtnNetwork build() {
            checkNotNull(id, "VTN network id cannot be null");
            checkNotNull(segmentId, "VTN network segment id cannot be null");
            checkNotNull(subnet, "VTN network subnet cannot be null");
            checkNotNull(serviceIp, "VTN network service IP cannot be null");
            checkNotNull(type, "VTN network type cannot be null");
            providers = providers == null ? ImmutableSet.of() : providers;

            return new VtnNetwork(id, segmentId, subnet, serviceIp, type, providers);
        }

        /**
         * Returns vtn network builder with the supplied network ID.
         *
         * @param id network id
         * @return vtn network builder
         */
        public Builder id(NetworkId id) {
            this.id = id;
            return this;
        }

        /**
         * Returns vtn network builder with the supplied segment ID.
         *
         * @param segmentId segment id
         * @return vtn network builder
         */
        public Builder segmentId(SegmentId segmentId) {
            this.segmentId = segmentId;
            return this;
        }

        /**
         * Returns vtn network builder with the supplied subnet.
         *
         * @param subnet subnet
         * @return vtn network builder
         */
        public Builder subnet(IpPrefix subnet) {
            this.subnet = subnet;
            return this;
        }

        /**
         * Returns vtn network service IP address.
         *
         * @param serviceIp service ip address
         * @return vtn network builder
         */
        public Builder serviceIp(IpAddress serviceIp) {
            this.serviceIp = serviceIp;
            return this;
        }

        /**
         * Returns vtn network builder with the supplied service network type.
         *
         * @param type service network type
         * @return vtn network builder
         */
        public Builder type(ServiceNetworkType type) {
            this.type = type;
            return this;
        }

        /**
         * Returns vtn network builder with the supplied provider service networks.
         *
         * @param providers provider service networks
         * @return vtn network builder
         */
        public Builder providers(Set<ProviderNetwork> providers) {
            this.providers = providers;
            return this;
        }

        /**
         * Returns vtn network builder with the given additional provider network.
         *
         * @param providerId provider network id
         * @param type       direct access type to the provider network
         * @return vtn network builder
         */
        public Builder addProvider(NetworkId providerId, Dependency.Type type) {
            checkNotNull(providerId, "Provider network ID cannot be null");
            checkNotNull(type, "Provider network type cannot be null");

            Set<ProviderNetwork> updated = Sets.newHashSet(this.providers);
            updated.add(ProviderNetwork.of(providerId, type));
            this.providers = ImmutableSet.copyOf(updated);
            return this;
        }

        /**
         * Returns vtn network builder without the given provider network.
         *
         * @param providerId provider network id
         * @return vtn network builder
         */
        public Builder delProvider(NetworkId providerId) {
            checkNotNull(providerId, "Provider network ID cannot be null");

            ProviderNetwork provider = this.providers.stream()
                    .filter(p -> Objects.equals(p.id(), providerId))
                    .findAny().orElse(null);
            if (provider != null) {
                Set<ProviderNetwork> updated = Sets.newHashSet(this.providers);
                updated.remove(provider);
                this.providers = ImmutableSet.copyOf(updated);
            }
            return this;
        }
    }
}
