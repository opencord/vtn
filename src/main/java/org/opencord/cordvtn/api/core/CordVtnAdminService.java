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
package org.opencord.cordvtn.api.core;

import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.NetworkService;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetworkService;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.api.net.SubnetId;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;

/**
 * Service for administering the inventory of {@link Network} and
 * {@link ServiceNetwork}.
 */
public interface CordVtnAdminService extends CordVtnService, NetworkService,
        ServiceNetworkService {

    /**
     * Purges internal network states.
     */
    void purgeStates();

    /**
     * Synchronizes internal network states with external services.
     */
    void syncStates();

    /**
     * Creates a service port with the given information.
     *
     * @param servicePort the new service port
     */
    void createServicePort(ServicePort servicePort);

    /**
     * Updates a service port with the given information.
     *
     * @param servicePort the updated service port
     */
    void updateServicePort(ServicePort servicePort);

    /**
     * Removes a service port with the given port id.
     *
     * @param portId port id
     */
    void removeServicePort(PortId portId);

    /**
     * Creates a service network with the given information.
     *
     * @param serviceNet the new service network
     */
    void createServiceNetwork(ServiceNetwork serviceNet);

    /**
     * Updates a service network with the given information.
     *
     * @param serviceNet the updated service network
     */
    void updateServiceNetwork(ServiceNetwork serviceNet);

    /**
     * Removes a service network with the given network id.
     *
     * @param netId network id
     */
    void removeServiceNetwork(NetworkId netId);

    /**
     * Creates a port.
     *
     * @param port port
     */
    void createPort(Port port);

    /**
     * Updates the port.
     *
     * @param port the updated port
     */
    void updatePort(Port port);

    /**
     * Removes the port with the given port id.
     *
     * @param portId port id
     */
    void removePort(PortId portId);

    /**
     * Creates a network.
     *
     * @param network network
     */
    void createNetwork(Network network);

    /**
     * Updates the network.
     *
     * @param network the updated network
     */
    void updateNetwork(Network network);

    /**
     * Removes the network with the given network id.
     *
     * @param netId network id
     */
    void removeNetwork(NetworkId netId);

    /**
     * Creates a subnet.
     *
     * @param subnet subnet id
     */
    void createSubnet(Subnet subnet);

    /**
     * Updates the subnet.
     *
     * @param subnet the updated subnet
     */
    void updateSubnet(Subnet subnet);

    /**
     * Removes the subnet with the given subnet id.
     *
     * @param subnetId subnet id
     */
    void removeSubnet(SubnetId subnetId);
}
