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
package org.opencord.cordvtn.api.core;

import org.onosproject.store.Store;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;

import java.util.Set;

/**
 * Manages inventory of service networks; not intended for direct use.
 */
public interface ServiceNetworkStore extends Store<ServiceNetworkEvent, ServiceNetworkStoreDelegate> {

    /**
     * Purges the service network store.
     */
    void clear();

    /**
     * Creates new service network.
     *
     * @param serviceNetwork service network
     */
    void createServiceNetwork(ServiceNetwork serviceNetwork);

    /**
     * Updates the service network.
     *
     * @param serviceNetwork service network
     */
    void updateServiceNetwork(ServiceNetwork serviceNetwork);

    /**
     * Returns the service network with the given network id.
     *
     * @param networkId network id
     * @return service network
     */
    ServiceNetwork serviceNetwork(NetworkId networkId);

    /**
     * Returns all service networks.
     *
     * @return set of service networks
     */
    Set<ServiceNetwork> serviceNetworks();

    /**
     * Removes the service network with the given network id.
     *
     * @param networkId network id
     * @return service network removed; null if failed
     */
    ServiceNetwork removeServiceNetwork(NetworkId networkId);

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
    ServicePort servicePort(PortId portId);

    /**
     * Returns all service ports.
     *
     * @return set of service ports
     */
    Set<ServicePort> servicePorts();

    /**
     * Removes service port.
     *
     * @param portId port id
     * @return service port removed
     */
    ServicePort removeServicePort(PortId portId);
}
