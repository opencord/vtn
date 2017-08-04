/*
 * Copyright 2016-present Open Networking Foundation
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

import org.onosproject.event.ListenerService;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;

import java.util.Set;

/**
 * Service for interacting with the inventory of {@link ServiceNetwork} and
 * {@link ServicePort}.
 */
public interface ServiceNetworkService
        extends ListenerService<ServiceNetworkEvent, ServiceNetworkListener> {

    /**
     * Returns the service network with the supplied network ID.
     *
     * @param networkId network id
     * @return service network
     */
    ServiceNetwork serviceNetwork(NetworkId networkId);

    /**
     * Returns all service networks registered in the service.
     *
     * @return set of service networks
     */
    Set<ServiceNetwork> serviceNetworks();

    /**
     * Returns the service port with the supplied port ID.
     *
     * @param portId port id
     * @return service port
     */
    ServicePort servicePort(PortId portId);

    /**
     * Returns all service ports registered in the service.
     *
     * @return set of service ports
     */
    Set<ServicePort> servicePorts();

    /**
     * Returns all service ports associated with the supplied network.
     * @param networkId network id
     * @return set of service ports
     */
    Set<ServicePort> servicePorts(NetworkId networkId);
}
