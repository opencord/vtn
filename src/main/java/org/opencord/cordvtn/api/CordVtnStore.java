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

import org.onosproject.store.Store;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;

import java.util.Set;

/**
 * Manages inventory of virtual and vtn networks; not intended for direct use.
 */
public interface CordVtnStore extends Store<VtnNetworkEvent, CordVtnStoreDelegate> {

    /**
     * Creates vtn network.
     *
     * @param serviceNet the new vtn network
     */
    void createVtnNetwork(VtnNetwork serviceNet);

    /**
     * Updates the vtn network.
     *
     * @param serviceNet the updated vtn network
     */
    void updateVtnNetwork(VtnNetwork serviceNet);

    /**
     * Returns the vtn network with the given network id.
     *
     * @param netId network id
     * @return vtn network
     */
    VtnNetwork getVtnNetwork(NetworkId netId);

    /**
     * Returns all vtn networks.
     *
     * @return set of vtn networks
     */
    Set<VtnNetwork> getVtnNetworks();

    /**
     * Removes the vtn network.
     *
     * @param netId network id
     */
    void removeVtnNetwork(NetworkId netId);

    /**
     * Creates vtn port.
     *
     * @param servicePort the new vtn port
     */
    void createVtnPort(VtnPort servicePort);

    /**
     * Updates the vtn port.
     *
     * @param servicePort vtn port
     */
    void updateVtnPort(VtnPort servicePort);

    /**
     * Returns the vtn port with the given port id.
     *
     * @param portId port id
     * @return vtn port
     */
    VtnPort getVtnPort(PortId portId);

    /**
     * Returns all vtn ports.
     *
     * @return set of vtn ports
     */
    Set<VtnPort> getVtnPorts();

    /**
     * Removes vtn port.
     *
     * @param portId port id
     */
    void removeVtnPort(PortId portId);

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
}
