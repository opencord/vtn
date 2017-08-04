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
package org.opencord.cordvtn.api.node;

import org.onosproject.event.ListenerService;
import org.onosproject.net.DeviceId;

import java.util.Set;

/**
 * Service for interfacing with the inventory of {@link CordVtnNode}.
 */
public interface CordVtnNodeService extends ListenerService<CordVtnNodeEvent, CordVtnNodeListener> {

    /**
     * Returns all nodes.
     *
     * @return set of nodes; empty set if no node presents
     */
    Set<CordVtnNode> nodes();

    /**
     * Returns nodes in complete state.
     *
     * @return set of nodes; empty set if no complete node presents
     */
    Set<CordVtnNode> completeNodes();

    /**
     * Returns the node with the given hostname.
     *
     * @param hostname hostname of the node
     * @return cordvtn node; null if no node present with the hostname
     */
    CordVtnNode node(String hostname);

    /**
     * Returns the node with the given integration bridge device identifier.
     *
     * @param deviceId integration bridge device id
     * @return cordvtn node; null if no node present with the device id
     */
    CordVtnNode node(DeviceId deviceId);
}
