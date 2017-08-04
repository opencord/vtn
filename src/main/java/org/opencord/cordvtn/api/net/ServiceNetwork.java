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
package org.opencord.cordvtn.api.net;

import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;

import java.util.Map;

/**
 * Representation of a service network.
 */
public interface ServiceNetwork {

    enum NetworkType {
        PRIVATE,
        PUBLIC,
        MANAGEMENT_HOST,
        MANAGEMENT_LOCAL,
        @Deprecated
        VSG,
        ACCESS_AGENT
    }

    enum DependencyType {
        BIDIRECTIONAL,
        UNIDIRECTIONAL
    }

    /**
     * Returns the service network identifier.
     *
     * @return service network identifier
     */
    NetworkId id();

    /**
     * Returns the service network name.
     *
     * @return service network name.
     */
    String name();

    /**
     * Returns the type of the service network.
     *
     * @return service network type; empty value if type is not set
     */
    NetworkType type();

    /**
     * Returns the service network segmentation identifier.
     *
     * @return segmentation id; empty value if segment id is not set
     */
    SegmentId segmentId();

    /**
     * Returns the subnet of the service network.
     *
     * @return subnet ip prefix; empty value if subnet is not set
     */
    IpPrefix subnet();

    /**
     * Returns the service IP address of the service network.
     *
     * @return service ip; empty value if service ip is not set
     */
    IpAddress serviceIp();

    /**
     * Returns the providers of the service network.
     *
     * @return set of provider networks; empty map if no providers exist
     */
    Map<NetworkId, DependencyType> providers();

    /**
     * Builder of new service network entities.
     */
    interface Builder {

        /**
         * Builds an immutable service network instance.
         *
         * @return service network instance
         */
        ServiceNetwork build();

        /**
         * Returns service network builder with the supplied identifier.
         *
         * @param networkId network id
         * @return service network builder
         */
        Builder id(NetworkId networkId);

        /**
         * Returns service network builder with the supplied name.
         *
         * @param name network name
         * @return service network builder
         */
        Builder name(String name);

        /**
         * Returns service network builder with the supplied type.
         *
         * @param type service network type
         * @return service network builder
         */
        Builder type(NetworkType type);

        /**
         * Returns service network builder with the supplied segmentation id.
         *
         * @param segmentId segmentation id
         * @return service network builder
         */
        Builder segmentId(SegmentId segmentId);

        /**
         * Returns service network builder with the supplied subnet.
         *
         * @param subnet subnet
         * @return service network builder
         */
        Builder subnet(IpPrefix subnet);

        /**
         * Returns service network builder with the supplied service IP address.
         *
         * @param serviceIp service ip address
         * @return service network builder
         */
        Builder serviceIp(IpAddress serviceIp);

        /**
         * Returns service network builder with the supplied providers.
         *
         * @param providers set of provider network
         * @return service network builder
         */
        Builder providers(Map<NetworkId, DependencyType> providers);
    }
}
