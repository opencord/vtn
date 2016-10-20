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

import org.onosproject.event.ListenerService;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.VtnNetwork;
import org.opencord.cordvtn.api.net.VtnNetworkEvent;
import org.opencord.cordvtn.api.net.VtnNetworkListener;
import org.opencord.cordvtn.api.net.VtnPort;

import java.util.Set;

/**
 * Service for interacting with the inventory of {@link VtnNetwork} and
 * {@link VtnPort}.
 */
public interface CordVtnService
        extends ListenerService<VtnNetworkEvent, VtnNetworkListener> {

    /**
     * Returns the VTN port with the given port id.
     *
     * @param portId port id
     * @return service port
     */
    VtnPort vtnPort(PortId portId);

    /**
     * Returns the VTN port with the given port name.
     *
     * @param portName port name
     * @return vtn port
     */
    VtnPort vtnPort(String portName);

    /**
     * Returns all VTN ports.
     *
     * @return set of service ports
     */
    Set<VtnPort> vtnPorts();

    /**
     * Returns the VTN network with the given network id.
     *
     * @param netId network id
     * @return service network
     */
    VtnNetwork vtnNetwork(NetworkId netId);

    /**
     * Returns all VTN networks.
     *
     * @return set of service networks
     */
    Set<VtnNetwork> vtnNetworks();
}
