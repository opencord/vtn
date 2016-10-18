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
 * Service for interfacing with external service for {@link ServiceNetwork} and
 * {@link ServicePort}.
 */
public interface ServiceNetworkService {

    /**
     * Returns all service network list.
     *
     * @return set of service network
     */
    Set<ServiceNetwork> serviceNetworks();

    /**
     * Returns service network with given network ID.
     *
     * @param netId network id
     * @return service network
     */
    ServiceNetwork serviceNetwork(NetworkId netId);

    /**
     * Returns all service port list.
     *
     * @return set of service port
     */
    Set<ServicePort> servicePorts();

    /**
     * Returns service port with given port ID.
     *
     * @param portId port id
     * @return service port
     */
    ServicePort servicePort(PortId portId);
}
