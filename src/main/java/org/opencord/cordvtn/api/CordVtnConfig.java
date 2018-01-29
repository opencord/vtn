/*
 * Copyright 2015-present Open Networking Foundation
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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.cluster.ClusterService;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.ControllerInfo;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.InvalidFieldException;
import org.opencord.cordvtn.api.net.CidrAddr;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.SshAccessInfo;
import org.opencord.cordvtn.impl.DefaultCordVtnNode;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.net.config.Config.FieldPresence.MANDATORY;
import static org.onosproject.net.config.Config.FieldPresence.OPTIONAL;
import static org.opencord.cordvtn.api.Constants.DEFAULT_OF_PORT;
import static org.opencord.cordvtn.api.Constants.DEFAULT_OF_PROTOCOL;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration object for CordVtn service.
 */
public class CordVtnConfig extends Config<ApplicationId> {

    protected final Logger log = getLogger(getClass());

    @Deprecated
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

    @Deprecated
    private static final String OPENSTACK = "openstack";
    private static final String OPENSTACK_ENDPOINT = "endpoint";
    private static final String OPENSTACK_TENANT = "tenant";
    private static final String OPENSTACK_USER = "user";
    private static final String OPENSTACK_PASSWORD = "password";
    @Deprecated
    private static final String XOS = "xos";
    private static final String XOS_ENDPOINT = "endpoint";
    private static final String XOS_USER = "user";
    private static final String XOS_PASSWORD = "password";

    private static final String CONTROLLERS = "controllers";
    private static final int INDEX_IP = 0;
    private static final int INDEX_PORT = 1;

    private final ClusterService clusterService =
            DefaultServiceDirectory.getService(ClusterService.class);

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
                CORDVTN_NODES,
                CONTROLLERS);

        if (object.get(CORDVTN_NODES) == null || object.get(CORDVTN_NODES).size() < 1) {
            final String msg = "No node is present";
            throw new IllegalArgumentException(msg);
        }

        // check all mandatory fields are present and valid
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

            CidrAddr localMgmt = CidrAddr.valueOf(get(LOCAL_MANAGEMENT_IP, ""));
            CidrAddr hostsMgmt = CidrAddr.valueOf(getConfig(vtnNode, HOST_MANAGEMENT_IP));
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

        // check all optional fields are valid
        result = result && isTpPort(OVSDB_PORT, OPTIONAL);

        if (object.get(PUBLIC_GATEWAYS) != null && object.get(PUBLIC_GATEWAYS).isArray()) {
            for (JsonNode node : object.get(PUBLIC_GATEWAYS)) {
                ObjectNode gateway = (ObjectNode) node;
                result = result && isIpAddress(gateway, GATEWAY_IP, MANDATORY);
                result = result && isMacAddress(gateway, GATEWAY_MAC, MANDATORY);
            }
        }

        if (object.get(CONTROLLERS) != null) {
            for (JsonNode jsonNode : object.get(CONTROLLERS)) {
                result = result && isController(jsonNode);
            }
        }
        return result;
    }

    private boolean isController(JsonNode jsonNode) {
        String[] ctrl = jsonNode.asText().split(":");
        final String error = "Malformed controller string " + jsonNode.asText() +
                ". Controller only takes a list of 'IP:port', 'IP', " +
                "or just one ':port'.";
        try {
            if (ctrl.length == 1) {
                IpAddress.valueOf(ctrl[INDEX_IP]);
                return true;
            }
            if (ctrl.length == 2 && ctrl[INDEX_IP].isEmpty() &&
                    object.get(CONTROLLERS).size() == 1) {
                TpPort.tpPort(Integer.valueOf(ctrl[INDEX_PORT]));
                return true;
            }
            if (ctrl.length == 2 && !ctrl[INDEX_IP].isEmpty()) {
                IpAddress.valueOf(ctrl[INDEX_IP]);
                TpPort.tpPort(Integer.valueOf(ctrl[INDEX_PORT]));
                return true;
            }
            throw new InvalidFieldException(CONTROLLERS, error);
        } catch (IllegalArgumentException e) {
            throw new InvalidFieldException(CONTROLLERS, error);
        }
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
            CidrAddr localMgmtIp = CidrAddr.valueOf(get(LOCAL_MANAGEMENT_IP, ""));
            CidrAddr hostsMgmtIp = CidrAddr.valueOf(getConfig(vtnNode, HOST_MANAGEMENT_IP));

            SshAccessInfo sshInfo = new SshAccessInfo(
                    hostsMgmtIp.ip().getIp4Address(),
                    TpPort.tpPort(Integer.parseInt(getConfig(sshNode, SSH_PORT))),
                    getConfig(sshNode, SSH_USER),
                    getConfig(sshNode, SSH_KEY_FILE));

            CordVtnNode.Builder nodeBuilder = DefaultCordVtnNode.builder()
                    .hostname(getConfig(vtnNode, HOSTNAME))
                    .hostManagementIp(hostsMgmtIp)
                    .localManagementIp(localMgmtIp)
                    .dataIp(CidrAddr.valueOf(getConfig(vtnNode, DATA_IP)))
                    .sshInfo(sshInfo)
                    .integrationBridgeId(DeviceId.deviceId(getConfig(vtnNode, INTEGRATION_BRIDGE_ID)))
                    .dataInterface(getConfig(vtnNode, DATA_IFACE));

            if (!Strings.isNullOrEmpty(ovsdbPort)) {
                nodeBuilder.ovsdbPort(TpPort.tpPort(Integer.parseInt(ovsdbPort)));
            }

            String hostMgmtIface = getConfig(vtnNode, HOST_MANAGEMENT_IFACE);
            if (!Strings.isNullOrEmpty(hostMgmtIface)) {
                nodeBuilder.hostManagementInterface(hostMgmtIface);
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
     * Returns controllers for the integration bridge.
     * It returns the information taken from cluster service with the default OF
     * port if no controller is specified in the network config.
     *
     * @return list of controller information
     */
    public List<ControllerInfo> controllers() {
        List<ControllerInfo> ctrls = Lists.newArrayList();
        JsonNode ctrlNodes = object.get(CONTROLLERS);

        if (ctrlNodes == null || isCtrlPortOnly()) {
            ctrls = clusterService.getNodes().stream()
                    .map(ctrl -> new ControllerInfo(
                            ctrl.ip(),
                            ctrlNodes == null ? DEFAULT_OF_PORT : getCtrlPort(),
                            DEFAULT_OF_PROTOCOL))
                    .collect(Collectors.toList());
        } else {
            for (JsonNode ctrlNode : ctrlNodes) {
                String[] ctrl = ctrlNode.asText().split(":");
                ctrls.add(new ControllerInfo(
                        IpAddress.valueOf(ctrl[INDEX_IP]),
                        ctrl.length == 1 ? DEFAULT_OF_PORT :
                                Integer.parseInt(ctrl[INDEX_PORT]),
                        DEFAULT_OF_PROTOCOL));
            }
        }
        return ImmutableList.copyOf(ctrls);
    }

    private boolean isCtrlPortOnly() {
        if (object.get(CONTROLLERS).size() != 1) {
            return false;
        }
        JsonNode jsonNode = object.get(CONTROLLERS).get(0);
        String[] ctrl = jsonNode.asText().split(":");
        return ctrl.length == 2 && ctrl[INDEX_IP].isEmpty();
    }

    private int getCtrlPort() {
        JsonNode jsonNode = object.get(CONTROLLERS).get(0);
        String[] ctrl = jsonNode.asText().split(":");
        return Integer.parseInt(ctrl[INDEX_PORT]);
    }

    public String getXosEndpoint() {
        JsonNode xosObject = object.get(XOS);
        if (xosObject == null) {
            return null;
        }
        return xosObject.get(XOS_ENDPOINT).asText();
    }

    public String getXosUsername() {
        JsonNode xosObject = object.get(XOS);
        if (xosObject == null) {
            return null;
        }
        return xosObject.get(XOS_USER).asText();
    }

    public String getXosPassword() {
        JsonNode xosObject = object.get(XOS);
        if (xosObject == null) {
            return null;
        }
        return xosObject.get(XOS_PASSWORD).asText();
    }

    public String getOpenstackEndpoint() {
        JsonNode xosObject = object.get(OPENSTACK);
        if (xosObject == null) {
            return null;
        }
        return xosObject.get(OPENSTACK_ENDPOINT).asText();
    }

    public String getOpenstackTenant() {
        JsonNode xosObject = object.get(OPENSTACK);
        if (xosObject == null) {
            return null;
        }
        return xosObject.get(OPENSTACK_TENANT).asText();
    }

    public String getOpenstackUser() {
        JsonNode xosObject = object.get(OPENSTACK);
        if (xosObject == null) {
            return null;
        }
        return xosObject.get(OPENSTACK_USER).asText();
    }

    public String getOpenstackPassword() {
        JsonNode xosObject = object.get(OPENSTACK);
        if (xosObject == null) {
            return null;
        }
        return xosObject.get(OPENSTACK_PASSWORD).asText();
    }
}
