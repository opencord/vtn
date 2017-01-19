/*
 * Copyright 2017-present Open Networking Laboratory
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
import org.onlab.packet.IpPrefix;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.SegmentId;
import org.opencord.cordvtn.api.net.ServiceNetwork;

import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.EMPTY_MAP;

/**
 * Implementation of {@link ServiceNetwork}.
 */
public final class DefaultServiceNetwork implements ServiceNetwork {

    private static final String ERR_ID_MISSING = "Service network ID cannot be null";

    private final NetworkId id;
    private final String name;
    private final NetworkType type;
    private final SegmentId segmentId;
    private final IpPrefix subnet;
    private final IpAddress serviceIp;
    private final Map<NetworkId, DependencyType> providers;

    private DefaultServiceNetwork(NetworkId id,
                                  String name,
                                  NetworkType type,
                                  SegmentId segmentId,
                                  IpPrefix subnet,
                                  IpAddress serviceIp,
                                  Map<NetworkId, DependencyType> providers) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.segmentId = segmentId;
        this.subnet = subnet;
        this.serviceIp = serviceIp;
        this.providers = providers;
    }

    @Override
    public NetworkId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public NetworkType type() {
        return type;
    }

    @Override
    public SegmentId segmentId() {
        return segmentId;
    }

    @Override
    public IpPrefix subnet() {
        return subnet;
    }

    @Override
    public IpAddress serviceIp() {
        return serviceIp;
    }

    @Override
    public Map<NetworkId, DependencyType> providers() {
        return providers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof DefaultServiceNetwork) {
            DefaultServiceNetwork that = (DefaultServiceNetwork) obj;
            if (Objects.equals(id, that.id) &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(type, that.type) &&
                    Objects.equals(segmentId, that.segmentId) &&
                    Objects.equals(subnet, that.subnet) &&
                    Objects.equals(serviceIp, that.serviceIp) &&
                    Objects.equals(providers, that.providers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, segmentId, subnet, serviceIp, type, providers);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("id", id)
                .add("name", name)
                .add("type", type)
                .add("segmentId", segmentId)
                .add("subnet", subnet)
                .add("serviceIp", serviceIp)
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
     * Returns new service network builder instance with copy of the given service network.
     *
     * @param snet service network
     * @return service network builder
     */
    public static Builder builder(ServiceNetwork snet) {
        return new Builder()
                .id(snet.id())
                .name(snet.name())
                .type(snet.type())
                .segmentId(snet.segmentId())
                .subnet(snet.subnet())
                .serviceIp(snet.serviceIp())
                .providers(snet.providers());
    }

    /**
     * Returns service network builder instance with updated values. Any value
     * not specified in the updated but in the existing remains in the updated
     * builder.
     *
     * @param existing existing service network
     * @param updated  updated service network
     * @return service network builder
     */
    public static Builder builder(ServiceNetwork existing, ServiceNetwork updated) {
        checkArgument(Objects.equals(existing.id(), updated.id()));
        // FIXME allow removing existing values
        return new Builder()
                .id(existing.id())
                .name(updated.name() != null ? updated.name() : existing.name())
                .type(updated.type() != null ? updated.type() : existing.type())
                .segmentId(updated.segmentId() != null ?
                                   updated.segmentId() : existing.segmentId())
                .subnet(updated.subnet() != null ? updated.subnet() : existing.subnet())
                .serviceIp(updated.serviceIp() != null ?
                                   updated.serviceIp() : existing.serviceIp())
                .providers(updated.providers() != EMPTY_MAP ?
                                   updated.providers() : existing.providers());
    }

    public static final class Builder implements ServiceNetwork.Builder {

        private NetworkId id;
        private String name;
        private NetworkType type;
        private SegmentId segmentId;
        private IpPrefix subnet;
        private IpAddress serviceIp;
        private Map<NetworkId, DependencyType> providers;

        private Builder() {
        }

        @Override
        public ServiceNetwork build() {
            checkNotNull(id, ERR_ID_MISSING);
            providers = providers != null ? providers : EMPTY_MAP;
            return new DefaultServiceNetwork(
                    id, name, type,
                    segmentId,
                    subnet, serviceIp,
                    providers);
        }

        @Override
        public Builder id(NetworkId id) {
            this.id = id;
            return this;
        }

        @Override
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder type(NetworkType type) {
            this.type = type;
            return this;
        }

        @Override
        public Builder segmentId(SegmentId segmentId) {
            this.segmentId = segmentId;
            return this;
        }

        @Override
        public Builder subnet(IpPrefix subnet) {
            this.subnet = subnet;
            return this;
        }

        @Override
        public Builder serviceIp(IpAddress serviceIp) {
            this.serviceIp = serviceIp;
            return this;
        }

        @Override
        public Builder providers(Map<NetworkId, DependencyType> providers) {
            this.providers = providers;
            return this;
        }
    }
}
