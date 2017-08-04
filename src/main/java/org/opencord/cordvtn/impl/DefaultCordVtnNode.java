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
package org.opencord.cordvtn.impl;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.onlab.packet.TpPort;
import org.onosproject.net.DeviceId;
import org.opencord.cordvtn.api.net.CidrAddr;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeState;
import org.opencord.cordvtn.api.node.SshAccessInfo;

import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opencord.cordvtn.api.Constants.DEFAULT_OVSDB_PORT;
import static org.opencord.cordvtn.api.Constants.DEFAULT_TUNNEL;

/**
 * Representation of a compute infrastructure node for CORD VTN service.
 */
public final class DefaultCordVtnNode implements CordVtnNode {

    private final String hostname;
    private final CidrAddr hostMgmtIp;
    private final CidrAddr localMgmtIp;
    private final CidrAddr dataIp;
    private final DeviceId integrationBridgeId;
    private final String dataIface;
    private final String hostMgmtIface;
    private final TpPort ovsdbPort;
    private final SshAccessInfo sshInfo;
    private final CordVtnNodeState state;

    private DefaultCordVtnNode(String hostname,
                               CidrAddr hostMgmtIp,
                               CidrAddr localMgmtIp,
                               CidrAddr dataIp,
                               DeviceId integrationBridgeId,
                               String dataIface,
                               String hostMgmtIface,
                               TpPort ovsdbPort,
                               SshAccessInfo sshInfo,
                               CordVtnNodeState state) {
        this.hostname = hostname;
        this.hostMgmtIp = hostMgmtIp;
        this.localMgmtIp = localMgmtIp;
        this.dataIp = dataIp;
        this.integrationBridgeId = integrationBridgeId;
        this.dataIface = dataIface;
        this.hostMgmtIface = hostMgmtIface;
        this.ovsdbPort = ovsdbPort;
        this.sshInfo = sshInfo;
        this.state = state;
    }

    /**
     * Returns cordvtn node with the new state.
     *
     * @param node cordvtn node
     * @param state cordvtn node state
     * @return cordvtn node
     */
    public static CordVtnNode updatedState(CordVtnNode node, CordVtnNodeState state) {
        return new DefaultCordVtnNode(node.hostname(),
                node.hostManagementIp(),
                node.localManagementIp(),
                node.dataIp(),
                node.integrationBridgeId(),
                node.dataInterface(),
                node.hostManagementInterface(),
                node.ovsdbPort(),
                node.sshInfo(),
                state);
    }

    @Override
    public String hostname() {
        return this.hostname;
    }

    @Override
    public CidrAddr hostManagementIp() {
        return this.hostMgmtIp;
    }

    @Override
    public CidrAddr localManagementIp() {
        return this.localMgmtIp;
    }

    @Override
    public CidrAddr dataIp() {
        return this.dataIp;
    }

    @Override
    public DeviceId integrationBridgeId() {
        return this.integrationBridgeId;
    }

    @Override
    public String dataInterface() {
        return this.dataIface;
    }

    @Override
    public String hostManagementInterface() {
        return this.hostMgmtIface;
    }

    @Override
    public TpPort ovsdbPort() {
        return this.ovsdbPort;
    }

    @Override
    public SshAccessInfo sshInfo() {
        return this.sshInfo;
    }

    @Override
    public CordVtnNodeState state() {
        return this.state;
    }

    @Override
    public DeviceId ovsdbId() {
        return DeviceId.deviceId("ovsdb:" + this.hostMgmtIp.ip().toString());
    }

    @Override
    public Set<String> systemInterfaces() {
        Set<String> ifaces = Sets.newHashSet(DEFAULT_TUNNEL, dataIface);
        if (hostMgmtIface != null) {
            ifaces.add(hostMgmtIface);
        }
        return ImmutableSet.copyOf(ifaces);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof DefaultCordVtnNode) {
            DefaultCordVtnNode that = (DefaultCordVtnNode) obj;
            if (Objects.equals(hostname, that.hostname) &&
                    Objects.equals(hostMgmtIp, that.hostMgmtIp) &&
                    Objects.equals(localMgmtIp, that.localMgmtIp) &&
                    Objects.equals(dataIp, that.dataIp) &&
                    Objects.equals(integrationBridgeId, that.integrationBridgeId) &&
                    Objects.equals(dataIface, that.dataIface) &&
                    Objects.equals(hostMgmtIface, that.hostMgmtIface) &&
                    Objects.equals(ovsdbPort, that.ovsdbPort) &&
                    Objects.equals(sshInfo, that.sshInfo)) {
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
                integrationBridgeId,
                dataIface,
                hostMgmtIface,
                ovsdbPort,
                sshInfo);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("hostname", hostname)
                .add("hostMgmtIp", hostMgmtIp)
                .add("localMgmtIp", localMgmtIp)
                .add("dataIp", dataIp)
                .add("integrationBridgeId", integrationBridgeId)
                .add("dataIface", dataIface)
                .add("hostMgmtIface", hostMgmtIface)
                .add("ovsdbPort", ovsdbPort)
                .add("sshInfo", sshInfo)
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

    public static final class Builder implements CordVtnNode.Builder {

        private String hostname;
        private CidrAddr hostMgmtIp;
        private CidrAddr localMgmtIp;
        private CidrAddr dataIp;
        private DeviceId integrationBridgeId;
        private String dataIface;
        private String hostMgmtIface;
        private TpPort ovsdbPort = TpPort.tpPort(DEFAULT_OVSDB_PORT);
        private SshAccessInfo sshInfo;
        private CordVtnNodeState state = CordVtnNodeState.INIT;

        private Builder() {
        }

        @Override
        public CordVtnNode build() {
            checkArgument(!Strings.isNullOrEmpty(hostname), "Hostname cannot be null");
            checkNotNull(hostMgmtIp, "Host management IP address cannot be null");
            checkNotNull(localMgmtIp, "Local management IP address cannot be null");
            checkNotNull(dataIp, "Data IP address cannot be null");
            checkNotNull(integrationBridgeId, "Integration bridge ID cannot be null");
            checkArgument(!Strings.isNullOrEmpty(dataIface), "Data interface cannot be null");
            if (hostMgmtIface != null) {
                checkArgument(!Strings.isNullOrEmpty(hostMgmtIface),
                        "Host management interface cannot be empty string");
            }
            checkNotNull(sshInfo, "SSH access information cannot be null");
            checkNotNull(state, "Node state cannot be null");

            return new DefaultCordVtnNode(hostname,
                    hostMgmtIp,
                    localMgmtIp,
                    dataIp,
                    integrationBridgeId,
                    dataIface,
                    hostMgmtIface,
                    ovsdbPort,
                    sshInfo, state);
        }

        @Override
        public Builder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        @Override
        public Builder hostManagementIp(CidrAddr hostMgmtIp) {
            this.hostMgmtIp = hostMgmtIp;
            return this;
        }

        @Override
        public Builder localManagementIp(CidrAddr localMgmtIp) {
            this.localMgmtIp = localMgmtIp;
            return this;
        }

        @Override
        public Builder dataIp(CidrAddr dataIp) {
            this.dataIp = dataIp;
            return this;
        }

        @Override
        public Builder integrationBridgeId(DeviceId deviceId) {
            this.integrationBridgeId = deviceId;
            return this;
        }

        @Override
        public Builder dataInterface(String dataIface) {
            this.dataIface = dataIface;
            return this;
        }

        @Override
        public Builder hostManagementInterface(String hostMgmtIface) {
            this.hostMgmtIface = hostMgmtIface;
            return this;
        }

        @Override
        public Builder ovsdbPort(TpPort port) {
            this.ovsdbPort = port;
            return this;
        }

        @Override
        public Builder sshInfo(SshAccessInfo sshInfo) {
            this.sshInfo = sshInfo;
            return this;
        }

        @Override
        public Builder state(CordVtnNodeState state) {
            this.state = state;
            return this;
        }
    }
}
