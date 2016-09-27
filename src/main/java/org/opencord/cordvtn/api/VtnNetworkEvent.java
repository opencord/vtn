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

import org.joda.time.LocalDateTime;
import org.onosproject.event.AbstractEvent;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * Describes vtn network event.
 */
public class VtnNetworkEvent extends AbstractEvent<VtnNetworkEvent.Type, VtnNetwork> {

    private final VtnPort vtnPort;

    /**
     * Type of vtn network event.
     */
    public enum Type {
        /**
         * Signifies that a new vtn network has been created.
         */
        VTN_NETWORK_CREATED,

        /**
         * Signifies that some vtn network attributes have changed.
         */
        VTN_NETWORK_UPDATED,

        /**
         * Signifies that a vtn network has been removed.
         */
        VTN_NETWORK_REMOVED,

        /**
         * Signifies that a new vtn port has been created.
         */
        VTN_PORT_CREATED,

        /**
         * Signifies that some vtn port attributes have changed.
         */
        VTN_PORT_UPDATED,

        /**
         * Signifies that a vtn port has been removed.
         */
        VTN_PORT_REMOVED
    }

    /**
     * Creates an event of a given type and for the specified vtn network and
     * the current time.
     *
     * @param type   vtn network event type
     * @param vtnNet vtn network subject
     */
    public VtnNetworkEvent(Type type, VtnNetwork vtnNet) {
        super(type, vtnNet);
        this.vtnPort = null;
    }

    /**
     * Creates an event of a given type and for the specified vtn network,
     * port and the current time.
     *
     * @param type    vtn network event type
     * @param vtnNet  vtn network subject
     * @param vtnPort optional vtn port subject
     */
    public VtnNetworkEvent(Type type, VtnNetwork vtnNet, VtnPort vtnPort) {
        super(type, vtnNet);
        this.vtnPort = vtnPort;
    }

    /**
     * Returns the vtn port subject.
     * It returns valid value only with the vtn port events.
     *
     * @return vtn port or null if the event is not vtn port specific
     */
    public VtnPort vtnPort() {
        return vtnPort;
    }

    @Override
    public String toString() {
        if (vtnPort == null) {
            return super.toString();
        }
        return toStringHelper(this)
                .add("time", new LocalDateTime(time()))
                .add("type", type())
                .add("vtnNet", subject())
                .add("vtnPort", vtnPort)
                .toString();
    }
}
