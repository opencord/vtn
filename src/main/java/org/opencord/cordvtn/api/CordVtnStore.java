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

import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;

import java.util.Set;

/**
 * Manages VTN service networks and ports.
 */
public interface CordVtnStore {

    /**
     * Creates service network.
     *
     * @param serviceNet the new service network
     */
    void createServiceNetwork(ServiceNetwork serviceNet);

    /**
     * Updates the service network.
     *
     * @param serviceNet the updated service network
     */
    void updateServiceNetwork(ServiceNetwork serviceNet);

    /**
     * Returns the service network with the given network id.
     *
     * @param netId network id
     * @return service network
     */
    ServiceNetwork getServiceNetwork(NetworkId netId);

    /**
     * Returns all service networks.
     *
     * @return set of service networks
     */
    Set<ServiceNetwork> getServiceNetworks();

    /**
     * Removes the service network.
     *
     * @param netId network id
     */
    void removeServiceNetwork(NetworkId netId);

    /**
     * Creates service port.
     *
     * @param servicePort the new service port
     */
    void createServicePort(ServicePort servicePort);

    /**
     * Updates the service port.
     *
     * @param servicePort service port
     */
    void updateServicePort(ServicePort servicePort);

    /**
     * Returns the service port with the given port id.
     *
     * @param portId port id
     * @return service port
     */
    ServicePort getServicePort(PortId portId);

    /**
     * Returns all service ports.
     *
     * @return set of service ports
     */
    Set<ServicePort> getServicePorts();

    /**
     * Removes service port.
     *
     * @param portId port id
     */
    void removeServicePort(PortId portId);

    /**
     * Creates a network.
     *
     * @param net network
     */
    void createNetwork(Network net);

    /**
     * Updates the network.
     *
     * @param net the updated network
     */
    void updateNetwork(Network net);

    /**
     * Returns the network with the given network id.
     *
     * @param netId network id
     * @return network
     */
    Network getNetwork(NetworkId netId);

    /**
     * Returns all networks.
     *
     * @return set of networks
     */
    Set<Network> getNetworks();

    /**
     * Removes the network with the given network id.
     *
     * @param netId network id
     */
    void removeNetwork(NetworkId netId);

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
     * Returns the port with the given port id.
     *
     * @param portId port id
     * @return port
     */
    Port getPort(PortId portId);

    /**
     * Returns all ports.
     *
     * @return set of ports
     */
    Set<Port> getPorts();

    /**
     * Removes the port with the given port id.
     *
     * @param portId port id
     */
    void removePort(PortId portId);

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
     * Returns the subnet with the given subnet id.
     *
     * @param subnetId subnet id
     * @return subnet
     */
    Subnet getSubnet(SubnetId subnetId);

    /**
     * Returns the subnet with the given network id. VTN does not allow multiple
     * subnets for a network.
     *
     * @param netId network id
     * @return subnet
     */
    Subnet getSubnet(NetworkId netId);

    /**
     * Returns all subnets.
     *
     * @return set of subnets
     */
    Set<Subnet> getSubnets();

    /**
     * Removes the subnet with the given subnet id.
     *
     * @param subnetId subnet id
     */
    void removeSubnet(SubnetId subnetId);

    /**
     * Returns the VTN network with the given network id.
     *
     * @param netId network id
     * @return vtn network
     */
    VtnNetwork getVtnNetwork(NetworkId netId);

    /**
     * Returns the VTN port with the given port id.
     *
     * @param portId port id
     * @return vtn port
     */
    VtnPort getVtnPort(PortId portId);
}
