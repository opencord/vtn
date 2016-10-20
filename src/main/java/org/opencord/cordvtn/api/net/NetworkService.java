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

import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;

import java.util.Set;

/**
 * Service for interacting with the inventory of {@link Network}, {@link Port},
 * and {@link Subnet}.
 */
public interface NetworkService {

    /**
     * Returns the network with the supplied network ID.
     *
     * @param netId network id
     * @return network
     */
    Network network(NetworkId netId);

    /**
     * Returns all networks registered in the service.
     *
     * @return set of networks
     */
    Set<Network> networks();

    /**
     * Returns the port with the supplied port ID.
     *
     * @param portId port id
     * @return port
     */
    Port port(PortId portId);

    /**
     * Returns all ports registered in the service.
     *
     * @return set of ports
     */
    Set<Port> ports();

    /**
     * Returns the subnet with the supplied subnet ID.
     *
     * @param subnetId subnet id
     * @return subnet
     */
    Subnet subnet(SubnetId subnetId);

    /**
     * Returns all subnets registered in the service.
     *
     * @return set of subnets
     */
    Set<Subnet> subnets();
}
