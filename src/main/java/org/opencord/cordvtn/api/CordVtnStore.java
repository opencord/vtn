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

    // TODO add apis for the virtual network and port
}
