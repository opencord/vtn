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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.onlab.packet.TpPort;
import org.onosproject.net.DeviceId;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opencord.cordvtn.api.Constants.DEFAULT_OVSDB_PORT;
import static org.opencord.cordvtn.api.Constants.DEFAULT_TUNNEL;

/**
 * Representation of a compute infrastructure node for CORD VTN service.
 */
public final class CordVtnNode {

    private final String hostname;
    private final NetworkAddress hostMgmtIp;
    private final NetworkAddress localMgmtIp;
    private final NetworkAddress dataIp;
    private final Optional<TpPort> ovsdbPort;
    private final SshAccessInfo sshInfo;
    private final DeviceId integrationBridgeId;
    private final String dataIface;
    private final Optional<String> hostMgmtIface;
    private final CordVtnNodeState state;

    public static final Comparator<CordVtnNode> CORDVTN_NODE_COMPARATOR =
            (node1, node2) -> node1.hostname().compareTo(node2.hostname());

    /**
     * Creates a new node.
     *
     * @param hostname hostname
     * @param hostMgmtIp host management network address
     * @param localMgmtIp local management network address
     * @param dataIp data network address
     * @param ovsdbPort port number for ovsdb connection
     * @param sshInfo ssh access information
     * @param integrationBridgeId integration bridge identifier
     * @param dataIface data plane interface name
     * @param hostMgmtIface host management network interface
     * @param state cordvtn node state
     */
    private CordVtnNode(String hostname,
                        NetworkAddress hostMgmtIp,
                        NetworkAddress localMgmtIp,
                        NetworkAddress dataIp,
                        Optional<TpPort> ovsdbPort,
                        SshAccessInfo sshInfo,
                        DeviceId integrationBridgeId,
                        String dataIface,
                        Optional<String> hostMgmtIface,
                        CordVtnNodeState state) {
        this.hostname = hostname;
        this.hostMgmtIp = hostMgmtIp;
        this.localMgmtIp = localMgmtIp;
        this.dataIp = dataIp;
        this.ovsdbPort = ovsdbPort;
        this.sshInfo = sshInfo;
        this.integrationBridgeId = integrationBridgeId;
        this.dataIface = dataIface;
        this.hostMgmtIface = hostMgmtIface;
        this.state = state;
    }

    /**
     * Returns cordvtn node with new state.
     *
     * @param node cordvtn node
     * @param state cordvtn node init state
     * @return cordvtn node
     */
    public static CordVtnNode getUpdatedNode(CordVtnNode node, CordVtnNodeState state) {
        return new CordVtnNode(node.hostname,
                               node.hostMgmtIp, node.localMgmtIp, node.dataIp,
                               node.ovsdbPort,
                               node.sshInfo,
                               node.integrationBridgeId,
                               node.dataIface, node.hostMgmtIface,
                               state);
    }

    /**
     * Returns the hostname.
     *
     * @return hostname
     */
    public String hostname() {
        return this.hostname;
    }

    /**
     * Returns the host management network address.
     *
     * @return network address
     */
    public NetworkAddress hostMgmtIp() {
        return this.hostMgmtIp;
    }

    /**
     * Returns the local management network address.
     *
     * @return network address
     */
    public NetworkAddress localMgmtIp() {
        return this.localMgmtIp;
    }

    /**
     * Returns the data network address.
     *
     * @return network address
     */
    public NetworkAddress dataIp() {
        return this.dataIp;
    }

    /**
     * Returns the port number used for OVSDB connection.
     * It returns default OVSDB port 6640, if it's not specified.
     *
     * @return port number
     */
    public TpPort ovsdbPort() {
        if (this.ovsdbPort.isPresent()) {
            return this.ovsdbPort.get();
        } else {
            return TpPort.tpPort(DEFAULT_OVSDB_PORT);
        }
    }

    /**
     * Returns the SSH access information.
     *
     * @return ssh access information
     */
    public SshAccessInfo sshInfo() {
        return this.sshInfo;
    }

    /**
     * Returns the identifier of the integration bridge.
     *
     * @return device id
     */
    public DeviceId integrationBridgeId() {
        return this.integrationBridgeId;
    }

    /**
     * Returns the identifier of the OVSDB device.
     *
     * @return device id
     */
    public DeviceId ovsdbId() {
        return DeviceId.deviceId("ovsdb:" + this.hostMgmtIp.ip().toString());
    }

    /**
     * Returns data network interface name.
     *
     * @return data network interface name
     */
    public String dataIface() {
        return this.dataIface;
    }

    /**
     * Returns host management network interface name.
     *
     * @return host management network interface name
     */
    public Optional<String> hostMgmtIface() {
        return this.hostMgmtIface;
    }

    /**
     * Returns a set of network interfaces for the VTN service to work properly.
     *
     * @return set of interface names
     */
    public Set<String> systemIfaces() {
        Set<String> ifaces = Sets.newHashSet(DEFAULT_TUNNEL, dataIface);
        if (hostMgmtIface.isPresent()) {
            ifaces.add(hostMgmtIface.get());
        }
        return ifaces;
    }

    /**
     * Returns the state of the node.
     *
     * @return state
     */
    public CordVtnNodeState state() {
        return this.state;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof CordVtnNode) {
            CordVtnNode that = (CordVtnNode) obj;
            if (Objects.equals(hostname, that.hostname) &&
                    Objects.equals(hostMgmtIp, that.hostMgmtIp) &&
                    Objects.equals(localMgmtIp, that.localMgmtIp) &&
                    Objects.equals(dataIp, that.dataIp) &&
                    Objects.equals(ovsdbPort, that.ovsdbPort) &&
                    Objects.equals(sshInfo, that.sshInfo) &&
                    Objects.equals(integrationBridgeId, that.integrationBridgeId) &&
                    Objects.equals(dataIface, that.dataIface) &&
                    Objects.equals(hostMgmtIface, that.hostMgmtIface)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname,
                            hostMgmtIp,
                            localMgmtIp,
                            dataIp,
                            ovsdbPort,
                            sshInfo,
                            integrationBridgeId,
                            dataIface,
                            hostMgmtIface);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("hostname", hostname)
                .add("hostMgmtIp", hostMgmtIp)
                .add("localMgmtIp", localMgmtIp)
                .add("dataIp", dataIp)
                .add("port", ovsdbPort)
                .add("sshInfo", sshInfo)
                .add("integrationBridgeId", integrationBridgeId)
                .add("dataIface", dataIface)
                .add("hostMgmtIface", hostMgmtIface)
                .add("state", state)
                .toString();
    }

    /**
     * Returns new node builder instance.
     *
     * @return cordvtn node builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of node entities.
     */
    public static final class Builder {
        private String hostname;
        private NetworkAddress hostMgmtIp;
        private NetworkAddress localMgmtIp;
        private NetworkAddress dataIp;
        private Optional<TpPort> ovsdbPort =
                Optional.of(TpPort.tpPort(DEFAULT_OVSDB_PORT));
        private SshAccessInfo sshInfo;
        private DeviceId integrationBridgeId;
        private String dataIface;
        private Optional<String> hostMgmtIface = Optional.empty();
        private CordVtnNodeState state = CordVtnNodeState.noState();

        private Builder() {
        }

        /**
         * Builds an immutable cordvtn node.
         *
         * @return cordvtn node
         */
        public CordVtnNode build() {
            // validate attributes
            checkArgument(!Strings.isNullOrEmpty(hostname));
            checkNotNull(hostMgmtIp);
            checkNotNull(localMgmtIp);
            checkNotNull(dataIp);
            checkNotNull(ovsdbPort);
            checkNotNull(sshInfo);
            checkNotNull(integrationBridgeId);
            checkNotNull(dataIface);
            checkNotNull(hostMgmtIface);
            return new CordVtnNode(hostname,
                                   hostMgmtIp, localMgmtIp, dataIp,
                                   ovsdbPort,
                                   sshInfo,
                                   integrationBridgeId,
                                   dataIface,
                                   hostMgmtIface,
                                   state);
        }

        /**
         * Returns cordvtn node builder with hostname.
         *
         * @param hostname hostname
         * @return cordvtn node builder
         */
        public Builder hostname(String hostname) {
            checkArgument(!Strings.isNullOrEmpty(hostname));
            this.hostname = hostname;
            return this;
        }

        /**
         * Returns cordvtn node builder with host management network IP address.
         *
         * @param hostMgmtIp host management netework ip address
         * @return cordvtn node builder
         */
        public Builder hostMgmtIp(NetworkAddress hostMgmtIp) {
            checkNotNull(hostMgmtIp);
            this.hostMgmtIp = hostMgmtIp;
            return this;
        }

        /**
         * Returns cordvtn node builder with host management network IP address.
         *
         * @param cidr string value of the host management network ip address
         * @return cordvtn node builder
         */
        public Builder hostMgmtIp(String cidr) {
            this.hostMgmtIp = NetworkAddress.valueOf(cidr);
            return this;
        }

        /**
         * Returns cordvtn node builder with local management network IP address.
         *
         * @param localMgmtIp local management network ip address
         * @return cordvtn node builder
         */
        public Builder localMgmtIp(NetworkAddress localMgmtIp) {
            checkNotNull(localMgmtIp);
            this.localMgmtIp = localMgmtIp;
            return this;
        }

        /**
         * Returns cordvtn node builder with local management netework IP address.
         *
         * @param cidr string value of the local management network ip address
         * @return cordvtn node builder
         */
        public Builder localMgmtIp(String cidr) {
            this.localMgmtIp = NetworkAddress.valueOf(cidr);
            return this;
        }

        /**
         * Returns cordvtn node builder with data network IP address.
         *
         * @param dataIp data network ip address
         * @return cordvtn node builder
         */
        public Builder dataIp(NetworkAddress dataIp) {
            checkNotNull(dataIp);
            this.dataIp = dataIp;
            return this;
        }

        /**
         * Returns cordvtn node builder with data network IP address.
         *
         * @param cidr string value of the data network ip address
         * @return cordvtn node builder
         */
        public Builder dataIp(String cidr) {
            this.dataIp = NetworkAddress.valueOf(cidr);
            return this;
        }

        /**
         * Returns cordvtn node builder with OVSDB server listen port number.
         *
         * @param port ovsdb server listen port number
         * @return cordvtn node builder
         */
        public Builder ovsdbPort(TpPort port) {
            checkNotNull(port);
            this.ovsdbPort = Optional.of(port);
            return this;
        }

        /**
         * Returns cordvtn node builder with OVSDB server listen port number.
         *
         * @param port int value of the ovsdb server listen port number
         * @return cordvtn node builder
         */
        public Builder ovsdbPort(int port) {
            this.ovsdbPort = Optional.of(TpPort.tpPort(port));
            return this;
        }

        /**
         * Returns cordvtn node builder with SSH access information.
         * @param sshInfo ssh access information
         * @return cordvtn node builder
         */
        public Builder sshInfo(SshAccessInfo sshInfo) {
            checkNotNull(sshInfo);
            this.sshInfo = sshInfo;
            return this;
        }

        /**
         * Returns cordvtn node builder with integration bridge ID.
         *
         * @param deviceId device id of the integration bridge
         * @return cordvtn node builder
         */
        public Builder integrationBridgeId(DeviceId deviceId) {
            checkNotNull(deviceId);
            this.integrationBridgeId = deviceId;
            return this;
        }

        /**
         * Returns cordvtn node builder with integration bridge ID.
         *
         * @param deviceId string value of the integration bridge device id
         * @return cordvtn node builder
         */
        public Builder integrationBridgeId(String deviceId) {
            this.integrationBridgeId = DeviceId.deviceId(deviceId);
            return this;
        }

        /**
         * Returns cordvtn node builder with data network interface name.
         *
         * @param dataIface data network interface name
         * @return cordvtn node builder
         */
        public Builder dataIface(String dataIface) {
            checkArgument(!Strings.isNullOrEmpty(dataIface));
            this.dataIface = dataIface;
            return this;
        }

        /**
         * Returns cordvtn node builder with host management network interface.
         *
         * @param hostMgmtIface host management network interface name
         * @return cordvtn node builder
         */
        public Builder hostMgmtIface(String hostMgmtIface) {
            this.hostMgmtIface = Optional.ofNullable(hostMgmtIface);
            return this;
        }

        /**
         * Returns cordvtn node builder with init state.
         *
         * @param state init state
         * @return cordvtn node builder
         */
        public Builder state(CordVtnNodeState state) {
            checkNotNull(state);
            this.state = state;
            return this;
        }
    }
}
