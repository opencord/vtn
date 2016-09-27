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

import org.onosproject.event.ListenerService;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;

import java.util.Set;

/**
 * Service for interacting with the inventory of VTN network and port.
 */
public interface CordVtnService
        extends ListenerService<VtnNetworkEvent, VtnNetworkListener> {

    /**
     * Returns the service port with the given port id.
     *
     * @param portId port id
     * @return service port
     */
    VtnPort getVtnPort(PortId portId);

    /**
     * Returns the vtn port with the given port id. It returns the VTN port with
     * the default settings if no service port exists for the port.
     *
     * @param portId port id
     * @return vtn port for the port, of the default vtn port if no service port
     * exists for the port
     */
    VtnPort getVtnPortOrDefault(PortId portId);

    /**
     * Returns the VTN port with the given port name.
     *
     * @param portName port name
     * @return vtn port
     */
    VtnPort getVtnPort(String portName);

    /**
     * Returns all service ports.
     *
     * @return set of service ports
     */
    Set<VtnPort> getVtnPorts();

    /**
     * Returns the service network with the given network id.
     *
     * @param netId network id
     * @return service network
     */
    VtnNetwork getVtnNetwork(NetworkId netId);

    /**
     * Returns the vtn network with the given network id. It returns the VTN
     * network with default settings if no service network exists for the network.
     *
     * @param netId network id
     * @return vtn network for the network id, or the default vtn network if no
     * service network is created for the network
     */
    VtnNetwork getVtnNetworkOrDefault(NetworkId netId);

    /**
     * Returns all service networks.
     *
     * @return set of service networks
     */
    Set<VtnNetwork> getVtnNetworks();

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
     * Returns instance attached to the given port.
     *
     * @param portId port identifier
     * @return instance, or null if no instance for the port
     */
    Instance getInstance(PortId portId);

    /**
     * Returns instances in the given network.
     *
     * @param netId network identifier
     * @return set of instances, empty set if no instances in the network
     */
    Set<Instance> getInstances(NetworkId netId);
}
