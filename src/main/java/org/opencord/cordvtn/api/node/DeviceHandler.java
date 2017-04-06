/*
 * Copyright 2017-present Open Networking Laboratory
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

import org.onosproject.net.Device;
import org.onosproject.net.Port;

/**
 * Entity capable of handling a device state updates.
 */
public interface DeviceHandler {

    /**
     * Processes the connected device.
     *
     * @param device device
     */
    void connected(Device device);

    /**
     * Processes the disconnected device.
     *
     * @param device device.
     */
    void disconnected(Device device);

    /**
     * Processes newly added port.
     *
     * @param port port
     */
    default void portAdded(Port port) {
        // do nothing by default
    }

    /**
     * Processes the updated port.
     *
     * @param port port
     */
    default void portUpdated(Port port) {
        // do nothing by default
    }

    /**
     * Processes the removed port.
     *
     * @param port port
     */
    default void portRemoved(Port port) {
        // do nothing by default
    }
}
