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
package org.opencord.cordvtn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.onosproject.net.config.Config.FieldPresence.MANDATORY;
import static org.onosproject.net.config.Config.FieldPresence.OPTIONAL;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration object for CordVtn service.
 */
public final class CordVtnConfig extends Config<ApplicationId> {

    protected final Logger log = getLogger(getClass());

    private static final String PRIVATE_GATEWAY_MAC = "privateGatewayMac";
    private static final String PUBLIC_GATEWAYS = "publicGateways";
    private static final String GATEWAY_IP = "gatewayIp";
    private static final String GATEWAY_MAC = "gatewayMac";
    private static final String LOCAL_MANAGEMENT_IP = "localManagementIp";
    private static final String OVSDB_PORT = "ovsdbPort";

    private static final String CORDVTN_NODES = "nodes";
    private static final String HOSTNAME = "hostname";
    private static final String HOST_MANAGEMENT_IP = "hostManagementIp";
    private static final String HOST_MANAGEMENT_IFACE = "hostManagementIface";
    private static final String DATA_IP = "dataPlaneIp";
    private static final String DATA_IFACE = "dataPlaneIntf";
    private static final String INTEGRATION_BRIDGE_ID = "bridgeId";

    private static final String SSH = "ssh";
    private static final String SSH_PORT = "sshPort";
    private static final String SSH_USER = "sshUser";
    private static final String SSH_KEY_FILE = "sshKeyFile";

    private static final String OPENSTACK = "openstack";
    private static final String XOS = "xos";

    private static final String ENDPOINT = "endpoint";
    private static final String TENANT = "tenant";
    private static final String USER = "user";
    private static final String PASSWORD = "password";

    // TODO implement isValid
    @Override
    public boolean isValid() {
        // check only allowed fields are present
        boolean result = hasOnlyFields(
                PRIVATE_GATEWAY_MAC,
                PUBLIC_GATEWAYS,
                LOCAL_MANAGEMENT_IP,
                OVSDB_PORT,
                SSH,
                OPENSTACK,
                XOS,
                CORDVTN_NODES);

        if (object.get(CORDVTN_NODES) == null || object.get(CORDVTN_NODES).size() < 1) {
            final String msg = "No node is present";
            throw new IllegalArgumentException(msg);
        }

        // check all mandatory fields are present and valid
        result = result && isMacAddress(PRIVATE_GATEWAY_MAC, MANDATORY);
        result = result && isIpPrefix(LOCAL_MANAGEMENT_IP, MANDATORY);

        for (JsonNode node : object.get(CORDVTN_NODES)) {
            ObjectNode vtnNode = (ObjectNode) node;
            result = result && hasFields(
                    vtnNode,
                    HOSTNAME,
                    HOST_MANAGEMENT_IP,
                    DATA_IP,
                    DATA_IFACE,
                    INTEGRATION_BRIDGE_ID);
            result = result && isIpPrefix(vtnNode, HOST_MANAGEMENT_IP, MANDATORY);
            result = result && isIpPrefix(vtnNode, DATA_IP, MANDATORY);

            NetworkAddress localMgmt = NetworkAddress.valueOf(get(LOCAL_MANAGEMENT_IP, ""));
            NetworkAddress hostsMgmt = NetworkAddress.valueOf(getConfig(vtnNode, HOST_MANAGEMENT_IP));
            if (hostsMgmt.prefix().contains(localMgmt.prefix()) ||
                    localMgmt.prefix().contains(hostsMgmt.prefix())) {
                final String msg = "Host and local management network IP conflict";
                throw new IllegalArgumentException(msg);
            }
        }

        result = result && hasFields(
                (ObjectNode) object.get(SSH),
                SSH_PORT,
                SSH_USER,
                SSH_KEY_FILE);
        result = result && isTpPort(
                (ObjectNode) object.get(SSH),
                SSH_PORT,
                MANDATORY);

        result = result && hasFields(
                (ObjectNode) object.get(OPENSTACK),
                ENDPOINT,
                TENANT,
                USER,
                PASSWORD);

        result = result && hasFields(
                (ObjectNode) object.get(XOS),
                ENDPOINT,
                USER,
                PASSWORD);

        // check all optional fields are valid
        result = result && isTpPort(OVSDB_PORT, OPTIONAL);

        if (object.get(PUBLIC_GATEWAYS) != null && object.get(PUBLIC_GATEWAYS).isArray()) {
            for (JsonNode node : object.get(PUBLIC_GATEWAYS)) {
                ObjectNode gateway = (ObjectNode) node;
                result = result && isIpAddress(gateway, GATEWAY_IP, MANDATORY);
                result = result && isMacAddress(gateway, GATEWAY_MAC, MANDATORY);
            }
        }
        return result;
    }

    /**
     * Returns the set of nodes read from network config.
     *
     * @return set of CordVtnNodeConfig or empty set
     */
    public Set<CordVtnNode> cordVtnNodes() {
        Set<CordVtnNode> nodes = Sets.newHashSet();
        JsonNode sshNode = object.get(SSH);
        String ovsdbPort = getConfig(object, OVSDB_PORT);

        object.get(CORDVTN_NODES).forEach(vtnNode -> {
            NetworkAddress localMgmt = NetworkAddress.valueOf(get(LOCAL_MANAGEMENT_IP, ""));
            NetworkAddress hostsMgmt = NetworkAddress.valueOf(getConfig(vtnNode, HOST_MANAGEMENT_IP));

            SshAccessInfo sshInfo = new SshAccessInfo(
                    hostsMgmt.ip().getIp4Address(),
                    TpPort.tpPort(Integer.parseInt(getConfig(sshNode, SSH_PORT))),
                    getConfig(sshNode, SSH_USER),
                    getConfig(sshNode, SSH_KEY_FILE));

            CordVtnNode.Builder nodeBuilder = CordVtnNode.builder()
                    .hostname(getConfig(vtnNode, HOSTNAME))
                    .hostMgmtIp(hostsMgmt)
                    .localMgmtIp(localMgmt)
                    .dataIp(getConfig(vtnNode, DATA_IP))
                    .sshInfo(sshInfo)
                    .integrationBridgeId(getConfig(vtnNode, INTEGRATION_BRIDGE_ID))
                    .dataIface(getConfig(vtnNode, DATA_IFACE));

            if (!Strings.isNullOrEmpty(ovsdbPort)) {
                nodeBuilder.ovsdbPort(Integer.parseInt(ovsdbPort));
            }

            String hostMgmtIface = getConfig(vtnNode, HOST_MANAGEMENT_IFACE);
            if (!Strings.isNullOrEmpty(hostMgmtIface)) {
                nodeBuilder.hostMgmtIface(hostMgmtIface);
            }

            nodes.add(nodeBuilder.build());
        });

        return nodes;
    }

    /**
     * Gets the specified property as a string.
     *
     * @param jsonNode node whose fields to get
     * @param path property to get
     * @return value as a string
     */
    private String getConfig(JsonNode jsonNode, String path) {
        jsonNode = jsonNode.path(path);
        return jsonNode.asText();
    }

    /**
     * Returns private network gateway MAC address.
     *
     * @return mac address
     */
    public MacAddress privateGatewayMac() {
        JsonNode jsonNode = object.get(PRIVATE_GATEWAY_MAC);
        return MacAddress.valueOf(jsonNode.asText());
    }

    /**
     * Returns public network gateway IP and MAC address pairs.
     *
     * @return map of ip and mac address
     */
    public Map<IpAddress, MacAddress> publicGateways() {
        JsonNode jsonNodes = object.get(PUBLIC_GATEWAYS);
        Map<IpAddress, MacAddress> publicGateways = Maps.newHashMap();

        if (jsonNodes == null) {
            return publicGateways;
        }

        jsonNodes.forEach(jsonNode -> publicGateways.put(
                IpAddress.valueOf(jsonNode.path(GATEWAY_IP).asText()),
                MacAddress.valueOf(jsonNode.path(GATEWAY_MAC).asText())));
        return publicGateways;
    }

    /**
     * Returns XOS API information.
     *
     * @return xos api endpoint and credentials
     */
    public XosConfig xosConfig() {
        JsonNode jsonNode = object.get(XOS);
        return new XosConfig(getConfig(jsonNode, ENDPOINT),
                             getConfig(jsonNode, USER),
                             getConfig(jsonNode, PASSWORD));
    }

    /**
     * Returns OpenStack API information.
     *
     * @return openstack api endpoint and credentials
     */
    public OpenStackConfig openStackConfig() {
        JsonNode jsonNode = object.get(OPENSTACK);
        return new OpenStackConfig(jsonNode.path(ENDPOINT).asText(),
                                   jsonNode.path(TENANT).asText(),
                                   jsonNode.path(USER).asText(),
                                   jsonNode.path(PASSWORD).asText());
    }

    /**
     * Representation of XOS API configuration.
     */
    public static final class XosConfig {
        private final String endpoint;
        private final String user;
        private final String password;

        /**
         * Default constructor.
         *
         * @param endpoint api endpoint
         * @param user user name
         * @param password password of the user
         */
        public XosConfig(String endpoint, String user, String password) {
            this.endpoint = endpoint;
            this.user = user;
            this.password = password;
        }

        /**
         * Returns the endpoint.
         *
         * @return endpoint
         */
        public String endpoint() {
            return endpoint;
        }

        /**
         * Returns the user.
         *
         * @return user
         */
        public String user() {
            return user;
        }

        /**
         * Returns the password.
         *
         * @return password
         */
        public String password() {
            return password;
        }

        @Override
        public int hashCode() {
            return Objects.hash(endpoint, user, password);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if ((obj instanceof XosConfig)) {
                XosConfig that = (XosConfig) obj;
                if (Objects.equals(endpoint, that.endpoint) &&
                        Objects.equals(user, that.user) &&
                        Objects.equals(password, that.password)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(getClass())
                    .add("endpoint", endpoint)
                    .add("user", user)
                    .add("password", password)
                    .toString();
        }
    }

    /**
     * Representation of OpenStack API configuration.
     */
    public static final class OpenStackConfig {
        private final String endpoint;
        private final String tenant;
        private final String user;
        private final String password;

        /**
         * Default constructor.
         *
         * @param endpoint api endpoint
         * @param tenant   tenant name
         * @param user user name
         * @param password password of the user
         */
        public OpenStackConfig(String endpoint, String tenant, String user,
                               String password) {
            this.endpoint = endpoint;
            this.tenant = tenant;
            this.user = user;
            this.password = password;
        }

        /**
         * Returns the endpoint.
         *
         * @return endpoint
         */
        public String endpoint() {
            return endpoint;
        }

        /**
         * Returns the tenant.
         *
         * @return tenant
         */
        public String tenant() {
            return tenant;
        }

        /**
         * Returns the user.
         *
         * @return user
         */
        public String user() {
            return user;
        }

        /**
         * Returns the password.
         *
         * @return password
         */
        public String password() {
            return password;
        }

        @Override
        public int hashCode() {
            return Objects.hash(endpoint, tenant, user, password);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if ((obj instanceof OpenStackConfig)) {
                OpenStackConfig that = (OpenStackConfig) obj;
                if (Objects.equals(endpoint, that.endpoint) &&
                        Objects.equals(tenant, that.tenant) &&
                        Objects.equals(user, that.user) &&
                        Objects.equals(password, that.password)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(getClass())
                    .add("endpoint", endpoint)
                    .add("tenant", tenant)
                    .add("user", user)
                    .add("password", password)
                    .toString();
        }
    }
}
