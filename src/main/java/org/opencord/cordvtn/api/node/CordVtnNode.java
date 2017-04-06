/*
 * Copyright 2015-present Open Networking Laboratory
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

import org.onlab.packet.TpPort;
import org.onosproject.net.DeviceId;
import org.opencord.cordvtn.api.net.CidrAddr;

import java.util.Set;

/**
 * Representation of a compute node for service instance provisioning.
 */
public interface CordVtnNode {

    /**
     * Returns the hostname of the node.
     *
     * @return hostname
     */
    String hostname();

    /**
     * Returns the host management IP address of the node.
     *
     * @return ip address with cidr notation
     */
    CidrAddr hostManagementIp();

    /**
     * Returns the local management IP address of the node.
     *
     * @return ip address with the cidr notation
     */
    // TODO remove this after dynamic provisioning of local management network
    CidrAddr localManagementIp();

    /**
     * Returns the data network IP address of the node.
     *
     * @return ip address with the cidr notation
     */
    CidrAddr dataIp();

    /**
     * Returns the integration bridge device identifier.
     *
     * @return device id
     */
    DeviceId integrationBridgeId();

    /**
     * Returns the data network interface name.
     *
     * @return interface name
     */
    String dataInterface();

    /**
     * Returns host management network interface name.
     *
     * @return interface name; null if not set
     */
    String hostManagementInterface();

    /**
     * Returns the port number of the OVSDB server.
     *
     * @return port number; 6640 if not set
     */
    TpPort ovsdbPort();

    /**
     * Returns the SSH access information.
     *
     * @return ssh access information
     */
    SshAccessInfo sshInfo();

    /**
     * Returns the state of the node.
     *
     * @return state
     */
    CordVtnNodeState state();

    /**
     * Returns the identifier of the OVSDB device.
     *
     * @return device id
     */
    DeviceId ovsdbId();

    /**
     * Returns system interfaces of the node.
     *
     * @return set of interface names
     */
    Set<String> systemInterfaces();

    /**
     * Builder of cordvtn node entities.
     */
    interface Builder {

        /**
         * Returns new cordvtn node.
         *
         * @return cordvtn node
         */
        CordVtnNode build();

        /**
         * Returns cordvtn node builder with the supplied hostname.
         *
         * @param hostname hostname of the node
         * @return cordvtn node builder
         */
        Builder hostname(String hostname);

        /**
         * Returns cordvtn node builder with the supplied host management IP.
         *
         * @param hostMgmtIp ip address with cidr notation
         * @return cordvtn node builder
         */
        Builder hostManagementIp(CidrAddr hostMgmtIp);

        /**
         * Returns cordvtn node builder with the supplied local management IP.
         *
         * @param localMgmtIp ip address with cidr notation
         * @return cordvtn node builder
         */
        // TODO remove this after dynamic provisioning of local management network
        Builder localManagementIp(CidrAddr localMgmtIp);

        /**
         * Returns cordvtn node builder with the supplied data IP.
         *
         * @param dataIp ip address with cidr notation
         * @return cordvtn node builder
         */
        Builder dataIp(CidrAddr dataIp);

        /**
         * Returns cordvtn node builder with the supplied integration bridge identifier.
         *
         * @param bridgeId bridge identifier
         * @return cordvtn node builder
         */
        Builder integrationBridgeId(DeviceId bridgeId);

        /**
         * Returns cordvtn node builder with the supplied data interface.
         *
         * @param dataIface interface name
         * @return cordvtn node builder
         */
        Builder dataInterface(String dataIface);

        /**
         * Returns cordvtn node builder with the supplied host management interface.
         *
         * @param hostMgmtIface interface name
         * @return cordvtn node builder
         */
        Builder hostManagementInterface(String hostMgmtIface);

        /**
         * Returns cordvtn node builder with the supplied OVSDB port.
         *
         * @param ovsdbPort transport layer port number
         * @return cordvtn node builder
         */
        Builder ovsdbPort(TpPort ovsdbPort);

        /**
         * Returns cordvtn node builder with the supplied SSH access information.
         *
         * @param sshInfo ssh access information
         * @return cordvtn node builder
         */
        Builder sshInfo(SshAccessInfo sshInfo);

        /**
         * Returns cordvtn node builder with the supplied initialize state.
         *
         * @param state cordvtn node state
         * @return cordvtn node builder
         */
        Builder state(CordVtnNodeState state);
    }
}
