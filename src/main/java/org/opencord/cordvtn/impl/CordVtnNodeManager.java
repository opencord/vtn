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
package org.opencord.cordvtn.impl;

import com.google.common.base.Strings;
import com.jcraft.jsch.Session;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.IpAddress;
import org.onlab.util.KryoNamespace;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.behaviour.BridgeDescription;
import org.onosproject.net.behaviour.DefaultBridgeDescription;
import org.onosproject.net.behaviour.InterfaceConfig;
import org.onosproject.net.behaviour.TunnelEndPoints;
import org.onosproject.net.behaviour.TunnelKeys;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.basics.SubjectFactories;
import org.opencord.cordvtn.api.node.ConnectionHandler;
import org.opencord.cordvtn.api.config.CordVtnConfig;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeState;
import org.opencord.cordvtn.api.instance.InstanceService;
import org.opencord.cordvtn.api.node.NetworkAddress;
import org.opencord.cordvtn.api.node.SshAccessInfo;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.BridgeConfig;
import org.onosproject.net.behaviour.BridgeName;
import org.onosproject.net.behaviour.ControllerInfo;
import org.onosproject.net.behaviour.DefaultTunnelDescription;
import org.onosproject.net.behaviour.TunnelDescription;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.ovsdb.controller.OvsdbClientService;
import org.onosproject.ovsdb.controller.OvsdbController;
import org.onosproject.ovsdb.controller.OvsdbNodeId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.MapEvent;
import org.onosproject.store.service.MapEventListener;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.Versioned;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.onosproject.net.Device.Type.SWITCH;
import static org.onosproject.net.behaviour.TunnelDescription.Type.VXLAN;
import static org.opencord.cordvtn.api.Constants.*;
import static org.opencord.cordvtn.impl.RemoteIpCommandUtil.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Reads node information from the network config file and handles the config
 * update events.
 * Only a leader controller performs the node addition or deletion.
 */
@Component(immediate = true)
@Service(value = CordVtnNodeManager.class)
public class CordVtnNodeManager {

    protected final Logger log = getLogger(getClass());

    private static final KryoNamespace.Builder NODE_SERIALIZER = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(CordVtnNode.class)
            .register(NodeState.class)
            .register(SshAccessInfo.class)
            .register(NetworkAddress.class);

    private static final int DPID_BEGIN = 3;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceAdminService adminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OvsdbController ovsdbController;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InstanceService instanceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    private static final Class<CordVtnConfig> CONFIG_CLASS = CordVtnConfig.class;
    private final ConfigFactory configFactory =
            new ConfigFactory<ApplicationId, CordVtnConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY, CONFIG_CLASS, "cordvtn") {
                @Override
                public CordVtnConfig createConfig() {
                    return new CordVtnConfig();
                }
            };

    private final ExecutorService eventExecutor =
            newSingleThreadExecutor(groupedThreads("onos/cordvtn-node", "event-handler", log));

    private final NetworkConfigListener configListener = new InternalConfigListener();
    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final MapEventListener<String, CordVtnNode> nodeStoreListener = new InternalMapListener();

    private final OvsdbHandler ovsdbHandler = new OvsdbHandler();
    private final BridgeHandler bridgeHandler = new BridgeHandler();

    private ConsistentMap<String, CordVtnNode> nodeStore;
    private ApplicationId appId;
    private NodeId localNodeId;

    private enum NodeState implements CordVtnNodeState {

        INIT {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                // make sure there is OVSDB connection
                if (!nodeManager.isOvsdbConnected(node)) {
                    nodeManager.connectOvsdb(node);
                    return;
                }
                nodeManager.createIntegrationBridge(node);
            }
        },
        BRIDGE_CREATED {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                // make sure there is OVSDB connection
                if (!nodeManager.isOvsdbConnected(node)) {
                    nodeManager.connectOvsdb(node);
                    return;
                }

                nodeManager.createTunnelInterface(node);
                nodeManager.addSystemInterface(node, node.dataIface());
                if (node.hostMgmtIface().isPresent()) {
                    nodeManager.addSystemInterface(node, node.hostMgmtIface().get());
                }
            }
        },
        PORTS_ADDED {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                nodeManager.setIpAddress(node);
            }
        },
        COMPLETE {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
                nodeManager.postInit(node);
            }
        },
        INCOMPLETE {
            @Override
            public void process(CordVtnNodeManager nodeManager, CordVtnNode node) {
            }
        };

        public abstract void process(CordVtnNodeManager nodeManager, CordVtnNode node);
    }

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(CORDVTN_APP_ID);

        configRegistry.registerConfigFactory(configFactory);
        localNodeId = clusterService.getLocalNode().id();
        leadershipService.runForLeadership(appId.name());

        nodeStore = storageService.<String, CordVtnNode>consistentMapBuilder()
                .withSerializer(Serializer.using(NODE_SERIALIZER.build()))
                .withName("cordvtn-nodestore")
                .withApplicationId(appId)
                .build();

        nodeStore.addListener(nodeStoreListener);
        deviceService.addListener(deviceListener);
        configService.addListener(configListener);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        configService.removeListener(configListener);
        deviceService.removeListener(deviceListener);
        nodeStore.removeListener(nodeStoreListener);

        leadershipService.withdraw(appId.name());
        configRegistry.unregisterConfigFactory(configFactory);
        eventExecutor.shutdown();

        log.info("Stopped");
    }

    /**
     * Adds or updates a new node to the service.
     *
     * @param node cordvtn node
     */
    public void addOrUpdateNode(CordVtnNode node) {
        checkNotNull(node);
        nodeStore.put(node.hostname(), CordVtnNode.getUpdatedNode(node, getNodeState(node)));
    }

    /**
     * Deletes a node from the service.
     *
     * @param node cordvtn node
     */
    public void deleteNode(CordVtnNode node) {
        checkNotNull(node);
        OvsdbClientService ovsdbClient = getOvsdbClient(node);
        if (ovsdbClient != null && ovsdbClient.isConnected()) {
            ovsdbClient.disconnect();
        }
        nodeStore.remove(node.hostname());
    }

    /**
     * Returns node initialization state.
     *
     * @param node cordvtn node
     * @return true if initial node setup is completed, otherwise false
     */
    public boolean isNodeInitComplete(CordVtnNode node) {
        checkNotNull(node);
        return isNodeStateComplete(node);
    }

    /**
     * Returns the number of the nodes known to the service.
     *
     * @return number of nodes
     */
    public int getNodeCount() {
        return nodeStore.size();
    }

    /**
     * Returns all nodes known to the service.
     *
     * @return list of nodes
     */
    public List<CordVtnNode> getNodes() {
        return nodeStore.values().stream().map(Versioned::value).collect(Collectors.toList());
    }

    /**
     * Returns all nodes in complete state.
     *
     * @return set of nodes
     */
    public Set<CordVtnNode> completeNodes() {
        return getNodes().stream().filter(this::isNodeStateComplete)
                .collect(Collectors.toSet());
    }

    /**
     * Returns physical data plane port number of a given device.
     *
     * @param deviceId integration bridge device id
     * @return port number; null otherwise
     */
    public PortNumber dataPort(DeviceId deviceId) {
        CordVtnNode node = nodeByBridgeId(deviceId);
        if (node == null) {
            log.warn("Failed to get node for {}", deviceId);
            return null;
        }

        Optional<PortNumber> port = getPortNumber(deviceId, node.dataIface());
        return port.isPresent() ? port.get() : null;
    }

    /**
     * Returns physical data plane IP address of a given device.
     *
     * @param deviceId integration bridge device id
     * @return ip address; null otherwise
     */
    public IpAddress dataIp(DeviceId deviceId) {
        CordVtnNode node = nodeByBridgeId(deviceId);
        if (node == null) {
            log.warn("Failed to get node for {}", deviceId);
            return null;
        }
        return node.dataIp().ip();
    }

    /**
     * Returns tunnel port number of a given device.
     *
     * @param deviceId integration bridge device id
     * @return port number
     */
    public PortNumber tunnelPort(DeviceId deviceId) {
        Optional<PortNumber> port = getPortNumber(deviceId, DEFAULT_TUNNEL);
        return port.isPresent() ? port.get() : null;
    }

    /**
     * Returns host management interface port number if exists.
     *
     * @param deviceId integration bridge device id
     * @return port number; null if it does not exist
     */
    public PortNumber hostManagementPort(DeviceId deviceId) {
        CordVtnNode node = nodeByBridgeId(deviceId);
        if (node == null) {
            log.warn("Failed to get node for {}", deviceId);
            return null;
        }

        if (node.hostMgmtIface().isPresent()) {
            Optional<PortNumber> port = getPortNumber(deviceId, node.hostMgmtIface().get());
            return port.isPresent() ? port.get() : null;
        } else {
            return null;
        }
    }

    private Optional<PortNumber> getPortNumber(DeviceId deviceId, String portName) {
        PortNumber port = deviceService.getPorts(deviceId).stream()
                .filter(p -> p.annotations().value(AnnotationKeys.PORT_NAME).equals(portName) &&
                        p.isEnabled())
                .map(Port::number)
                .findAny()
                .orElse(null);
        return Optional.ofNullable(port);
    }

    /**
     * Returns if current node state saved in nodeStore is COMPLETE or not.
     *
     * @param node cordvtn node
     * @return true if it's complete state, otherwise false
     */
    private boolean isNodeStateComplete(CordVtnNode node) {
        checkNotNull(node);

        // the state saved in nodeStore can be wrong if IP address settings are changed
        // after the node init has been completed since there's no way to detect it
        // getNodeState and checkNodeInitState always return correct answer but can be slow
        Versioned<CordVtnNode> versionedNode = nodeStore.get(node.hostname());
        CordVtnNodeState state = versionedNode.value().state();
        return state != null && state.equals(NodeState.COMPLETE);
    }

    /**
     * Initiates node to serve virtual tenant network.
     *
     * @param node cordvtn node
     */
    private void initNode(CordVtnNode node) {
        checkNotNull(node);

        NodeState state = (NodeState) node.state();
        log.debug("Processing node: {} state: {}", node.hostname(), state);

        state.process(this, node);
    }

    /**
     * Performs tasks after node initialization.
     * It disconnects unnecessary OVSDB connection and installs initial flow
     * rules on the device.
     *
     * @param node cordvtn node
     */
    private void postInit(CordVtnNode node) {
        checkNotNull(node);

        // disconnect OVSDB session once the node bootstrap is done
        OvsdbClientService ovsdbClient = getOvsdbClient(node);
        if (ovsdbClient != null && ovsdbClient.isConnected()) {
            ovsdbClient.disconnect();
        }

        pipeline.initPipeline(node);

        // adds existing instances to the host list
        deviceService.getPorts(node.integrationBridgeId()).stream()
                .filter(port -> !node.systemIfaces().contains(portName(port)) &&
                        !port.number().equals(PortNumber.LOCAL) &&
                        port.isEnabled())
                .forEach(port -> instanceService.addInstance(connectPoint(port)));

        // removes stale instances from the host list
        hostService.getHosts().forEach(host -> {
            if (deviceService.getPort(
                    host.location().deviceId(),
                    host.location().port()) == null) {
                instanceService.removeInstance(host.location());
            }
        });

        log.info("Finished init {}", node.hostname());
    }

    /**
     * Sets a new state for a given cordvtn node.
     *
     * @param node cordvtn node
     * @param newState new node state
     */
    private void setNodeState(CordVtnNode node, NodeState newState) {
        checkNotNull(node);
        log.debug("Changed {} state: {}", node.hostname(), newState);
        nodeStore.put(node.hostname(), CordVtnNode.getUpdatedNode(node, newState));
    }

    /**
     * Checks current state of a given cordvtn node and returns it.
     *
     * @param node cordvtn node
     * @return node state
     */
    private NodeState getNodeState(CordVtnNode node) {
        checkNotNull(node);
        if (!isIntegrationBridgeCreated(node)) {
            return NodeState.INIT;
        }
        for (String iface : node.systemIfaces()) {
            if (!isIfaceCreated(node, iface)) {
                return NodeState.BRIDGE_CREATED;
            }
        }
        if (!isIpAddressSet(node)) {
            return NodeState.PORTS_ADDED;
        }
        return NodeState.COMPLETE;
    }

    /**
     * Returns connection state of OVSDB server for a given node.
     *
     * @param node cordvtn node
     * @return true if it is connected, false otherwise
     */
    private boolean isOvsdbConnected(CordVtnNode node) {
        checkNotNull(node);
        OvsdbClientService ovsdbClient = getOvsdbClient(node);
        return deviceService.isAvailable(node.ovsdbId()) &&
                ovsdbClient != null &&
                ovsdbClient.isConnected();
    }

    /**
     * Connects to OVSDB server for a given node.
     *
     * @param node cordvtn node
     */
    private void connectOvsdb(CordVtnNode node) {
        checkNotNull(node);
        ovsdbController.connect(node.hostMgmtIp().ip(), node.ovsdbPort());
    }

    /**
     * Returns OVSDB client for a given node.
     *
     * @param node cordvtn node
     * @return ovsdb client, or null if there's no ovsdb connection
     */
    private OvsdbClientService getOvsdbClient(CordVtnNode node) {
        checkNotNull(node);
        OvsdbNodeId ovsdb = new OvsdbNodeId(
                node.hostMgmtIp().ip(), node.ovsdbPort().toInt());
        return ovsdbController.getOvsdbClient(ovsdb);
    }

    private void createIntegrationBridge(CordVtnNode node) {
        Device device = deviceService.getDevice(node.ovsdbId());
        if (device == null || !device.is(BridgeConfig.class)) {
            log.error("Failed to create integration bridge on {}", node.ovsdbId());
            return;
        }

        List<ControllerInfo> controllers = clusterService.getNodes().stream()
                .map(controller -> new ControllerInfo(controller.ip(), OF_PORT, "tcp"))
                .collect(Collectors.toList());

        String dpid = node.integrationBridgeId().toString().substring(DPID_BEGIN);
        BridgeDescription bridgeDesc = DefaultBridgeDescription.builder()
                .name(INTEGRATION_BRIDGE)
                .failMode(BridgeDescription.FailMode.SECURE)
                .datapathId(dpid)
                .disableInBand()
                .controllers(controllers)
                .build();

        BridgeConfig bridgeConfig =  device.as(BridgeConfig.class);
        bridgeConfig.addBridge(bridgeDesc);
    }

    private void createTunnelInterface(CordVtnNode node) {
        Device device = deviceService.getDevice(node.ovsdbId());
        if (device == null || !device.is(InterfaceConfig.class)) {
            log.error("Failed to create tunnel interface on {}", node.ovsdbId());
            return;
        }

        TunnelDescription tunnelDesc = DefaultTunnelDescription.builder()
                .deviceId(INTEGRATION_BRIDGE)
                .ifaceName(DEFAULT_TUNNEL)
                .type(VXLAN)
                .remote(TunnelEndPoints.flowTunnelEndpoint())
                .key(TunnelKeys.flowTunnelKey())
                .build();

        InterfaceConfig ifaceConfig = device.as(InterfaceConfig.class);
        ifaceConfig.addTunnelMode(DEFAULT_TUNNEL, tunnelDesc);
    }

    private void addSystemInterface(CordVtnNode node, String ifaceName) {
        Session session = connect(node.sshInfo());
        if (session == null || !isInterfaceUp(session, ifaceName)) {
            log.warn("Interface {} is not available on {}", ifaceName, node.hostname());
            disconnect(session);
            return;
        } else {
            disconnect(session);
        }

        Device device = deviceService.getDevice(node.ovsdbId());
        if (!device.is(BridgeConfig.class)) {
            log.error("BridgeConfig is not supported for {}", node.ovsdbId());
            return;
        }

        BridgeConfig bridgeConfig = device.as(BridgeConfig.class);
        bridgeConfig.addPort(BridgeName.bridgeName(INTEGRATION_BRIDGE), ifaceName);
    }

    /**
     * Flushes IP address from data plane interface and adds data plane IP address
     * to integration bridge.
     *
     * @param node cordvtn node
     */
    private void setIpAddress(CordVtnNode node) {
        Session session = connect(node.sshInfo());
        if (session == null) {
            log.debug("Failed to SSH to {}", node.hostname());
            return;
        }
        getCurrentIps(session, INTEGRATION_BRIDGE).stream()
                .filter(ip -> !ip.equals(node.localMgmtIp().ip()))
                .filter(ip -> !ip.equals(node.dataIp().ip()))
                .forEach(ip -> deleteIp(session, ip, INTEGRATION_BRIDGE));

        boolean result = flushIp(session, node.dataIface()) &&
                setInterfaceUp(session, node.dataIface()) &&
                addIp(session, node.dataIp(), INTEGRATION_BRIDGE) &&
                addIp(session, node.localMgmtIp(), INTEGRATION_BRIDGE) &&
                setInterfaceUp(session, INTEGRATION_BRIDGE);

        disconnect(session);
        if (result) {
            setNodeState(node, NodeState.COMPLETE);
        }
    }

    private boolean isIntegrationBridgeCreated(CordVtnNode node) {
        return deviceService.getDevice(node.integrationBridgeId()) != null &&
                deviceService.isAvailable(node.integrationBridgeId());
    }

    private boolean isIfaceCreated(CordVtnNode node, String ifaceName) {
        if (Strings.isNullOrEmpty(ifaceName)) {
            return false;
        }
        return deviceService.getPorts(node.integrationBridgeId()).stream()
                .filter(p -> portName(p).contains(ifaceName) &&
                        p.isEnabled())
                .findAny()
                .isPresent();
    }

    /**
     * Checks if the IP addresses are correctly set.
     *
     * @param node cordvtn node
     * @return true if the IP is set, false otherwise
     */
    private boolean isIpAddressSet(CordVtnNode node) {
        Session session = connect(node.sshInfo());
        if (session == null) {
            log.debug("Failed to SSH to {}", node.hostname());
            return false;
        }

        Set<IpAddress> intBrIps = getCurrentIps(session, INTEGRATION_BRIDGE);
        boolean result = getCurrentIps(session, node.dataIface()).isEmpty() &&
                isInterfaceUp(session, node.dataIface()) &&
                intBrIps.contains(node.dataIp().ip()) &&
                intBrIps.contains(node.localMgmtIp().ip()) &&
                isInterfaceUp(session, INTEGRATION_BRIDGE);

        disconnect(session);
        return result;
    }

    /**
     * Returns connect point of a given port.
     *
     * @param port port
     * @return connect point
     */
    private ConnectPoint connectPoint(Port port) {
        return new ConnectPoint(port.element().id(), port.number());
    }

    /**
     * Returns cordvtn node associated with a given OVSDB device.
     *
     * @param ovsdbId OVSDB device id
     * @return cordvtn node, null if it fails to find the node
     */
    private CordVtnNode nodeByOvsdbId(DeviceId ovsdbId) {
        return getNodes().stream()
                .filter(node -> node.ovsdbId().equals(ovsdbId))
                .findFirst().orElse(null);
    }

    /**
     * Returns cordvtn node associated with a given integration bridge.
     *
     * @param bridgeId device id of integration bridge
     * @return cordvtn node, null if it fails to find the node
     */
    private CordVtnNode nodeByBridgeId(DeviceId bridgeId) {
        return getNodes().stream()
                .filter(node -> node.integrationBridgeId().equals(bridgeId))
                .findFirst().orElse(null);
    }

    /**
     * Returns port name.
     *
     * @param port port
     * @return port name
     */
    private String portName(Port port) {
        return port.annotations().value(PORT_NAME);
    }

    private class OvsdbHandler implements ConnectionHandler<Device> {

        @Override
        public void connected(Device device) {
            CordVtnNode node = nodeByOvsdbId(device.id());
            if (node != null) {
                setNodeState(node, getNodeState(node));
            } else {
                log.debug("{} is detected on unregistered node, ignore it.", device.id());
            }
        }

        @Override
        public void disconnected(Device device) {
            log.debug("Device {} is disconnected", device.id());
            adminService.removeDevice(device.id());
        }
    }

    private class BridgeHandler implements ConnectionHandler<Device> {

        @Override
        public void connected(Device device) {
            CordVtnNode node = nodeByBridgeId(device.id());
            if (node != null) {
                setNodeState(node, getNodeState(node));
            } else {
                log.debug("{} is detected on unregistered node, ignore it.", device.id());
            }
        }

        @Override
        public void disconnected(Device device) {
            CordVtnNode node = nodeByBridgeId(device.id());
            if (node != null) {
                log.warn("Integration Bridge is disconnected from {}", node.hostname());
                setNodeState(node, NodeState.INCOMPLETE);
            }
        }

        /**
         * Handles port added situation.
         * If the added port is tunnel or data plane interface, proceed to the remaining
         * node initialization. Otherwise, do nothing.
         *
         * @param port port
         */
        public void portAdded(Port port) {
            CordVtnNode node = nodeByBridgeId((DeviceId) port.element().id());
            String portName = portName(port);

            if (node == null) {
                log.debug("{} is added to unregistered node, ignore it.", portName);
                return;
            }

            log.info("Port {} is added to {}", portName, node.hostname());

            if (node.systemIfaces().contains(portName)) {
                setNodeState(node, getNodeState(node));
            } else if (isNodeStateComplete(node)) {
                instanceService.addInstance(connectPoint(port));
            } else {
                log.warn("Instance is detected on incomplete node, ignore it.", portName);
            }
        }

        /**
         * Handles port removed situation.
         * If the removed port is tunnel or data plane interface, proceed to the remaining
         * node initialization.Others, do nothing.
         *
         * @param port port
         */
        public void portRemoved(Port port) {
            CordVtnNode node = nodeByBridgeId((DeviceId) port.element().id());
            String portName = portName(port);

            if (node == null) {
                return;
            }

            log.info("Port {} is removed from {}", portName, node.hostname());

            if (node.systemIfaces().contains(portName)) {
                setNodeState(node, NodeState.INCOMPLETE);
            } else if (isNodeStateComplete(node)) {
                instanceService.removeInstance(connectPoint(port));
            } else {
                log.warn("VM is vanished from incomplete node, ignore it.", portName);
            }
        }
    }

    private class InternalDeviceListener implements DeviceListener {

        @Override
        public void event(DeviceEvent event) {

            NodeId leaderNodeId = leadershipService.getLeader(appId.name());
            if (!Objects.equals(localNodeId, leaderNodeId)) {
                // do not allow to proceed without leadership
                return;
            }

            Device device = event.subject();
            ConnectionHandler<Device> handler =
                    (device.type().equals(SWITCH) ? bridgeHandler : ovsdbHandler);

            switch (event.type()) {
                case PORT_ADDED:
                    eventExecutor.execute(() -> bridgeHandler.portAdded(event.port()));
                    break;
                case PORT_UPDATED:
                    if (!event.port().isEnabled()) {
                        eventExecutor.execute(() -> bridgeHandler.portRemoved(event.port()));
                    }
                    break;
                case DEVICE_ADDED:
                case DEVICE_AVAILABILITY_CHANGED:
                    if (deviceService.isAvailable(device.id())) {
                        eventExecutor.execute(() -> handler.connected(device));
                    } else {
                        eventExecutor.execute(() -> handler.disconnected(device));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Reads cordvtn nodes from config file.
     */
    private void readConfiguration() {
        CordVtnConfig config = configRegistry.getConfig(appId, CordVtnConfig.class);
        if (config == null) {
            log.debug("No configuration found");
            return;
        }
        config.cordVtnNodes().forEach(this::addOrUpdateNode);
    }

    private class InternalConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            NodeId leaderNodeId = leadershipService.getLeader(appId.name());
            if (!Objects.equals(localNodeId, leaderNodeId)) {
                // do not allow to proceed without leadership
                return;
            }

            if (!event.configClass().equals(CordVtnConfig.class)) {
                return;
            }

            switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                    eventExecutor.execute(CordVtnNodeManager.this::readConfiguration);
                    break;
                default:
                    break;
            }
        }
    }

    private class InternalMapListener implements MapEventListener<String, CordVtnNode> {

        @Override
        public void event(MapEvent<String, CordVtnNode> event) {
            NodeId leaderNodeId = leadershipService.getLeader(appId.name());
            if (!Objects.equals(localNodeId, leaderNodeId)) {
                // do not allow to proceed without leadership
                return;
            }

            CordVtnNode oldNode;
            CordVtnNode newNode;

            switch (event.type()) {
                case UPDATE:
                    oldNode = event.oldValue().value();
                    newNode = event.newValue().value();

                    log.info("Reloaded {}", newNode.hostname());
                    if (!newNode.equals(oldNode)) {
                        log.debug("New node: {}", newNode);
                    }
                    // performs init procedure even if the node is not changed
                    // for robustness since it's no harm to run init procedure
                    // multiple times
                    eventExecutor.execute(() -> initNode(newNode));
                    break;
                case INSERT:
                    newNode = event.newValue().value();
                    log.info("Added {}", newNode.hostname());
                    eventExecutor.execute(() -> initNode(newNode));
                    break;
                case REMOVE:
                    oldNode = event.oldValue().value();
                    log.info("Removed {}", oldNode.hostname());
                    break;
                default:
                    break;
            }
        }
    }
}
