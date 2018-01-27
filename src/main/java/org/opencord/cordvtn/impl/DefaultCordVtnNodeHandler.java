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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.jcraft.jsch.Session;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.IpAddress;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.BridgeConfig;
import org.onosproject.net.behaviour.BridgeDescription;
import org.onosproject.net.behaviour.BridgeName;
import org.onosproject.net.behaviour.ControllerInfo;
import org.onosproject.net.behaviour.DefaultBridgeDescription;
import org.onosproject.net.behaviour.DefaultTunnelDescription;
import org.onosproject.net.behaviour.InterfaceConfig;
import org.onosproject.net.behaviour.TunnelDescription;
import org.onosproject.net.behaviour.TunnelEndPoints;
import org.onosproject.net.behaviour.TunnelKeys;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.ovsdb.controller.OvsdbClientService;
import org.onosproject.ovsdb.controller.OvsdbController;
import org.onosproject.ovsdb.controller.OvsdbNodeId;
import org.opencord.cordvtn.api.CordVtnConfig;
import org.opencord.cordvtn.api.core.CordVtnPipeline;
import org.opencord.cordvtn.api.core.InstanceService;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeAdminService;
import org.opencord.cordvtn.api.node.CordVtnNodeEvent;
import org.opencord.cordvtn.api.node.CordVtnNodeHandler;
import org.opencord.cordvtn.api.node.CordVtnNodeListener;
import org.opencord.cordvtn.api.node.CordVtnNodeService;
import org.opencord.cordvtn.api.node.CordVtnNodeState;
import org.opencord.cordvtn.api.node.DeviceHandler;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.onosproject.net.Device.Type.SWITCH;
import static org.onosproject.net.behaviour.TunnelDescription.Type.VXLAN;
import static org.opencord.cordvtn.api.Constants.CORDVTN_APP_ID;
import static org.opencord.cordvtn.api.Constants.DEFAULT_TUNNEL;
import static org.opencord.cordvtn.api.Constants.INTEGRATION_BRIDGE;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.*;
import static org.opencord.cordvtn.impl.DefaultCordVtnNode.updatedState;
import static org.opencord.cordvtn.impl.RemoteIpCommandUtil.*;
import static org.opencord.cordvtn.impl.RemoteIpCommandUtil.disconnect;
import static org.opencord.cordvtn.impl.RemoteIpCommandUtil.isInterfaceUp;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of the {@link CordVtnNodeHandler} with OVSDB and SSH exec channel.
 */
@Component(immediate = true)
@Service
public class DefaultCordVtnNodeHandler implements CordVtnNodeHandler {

    protected final Logger log = getLogger(getClass());

    private static final String ERR_INCOMPLETE = "%s is %s from incomplete node, ignore it";
    private static final String ERR_UNREGISTERED = "%s is %s from unregistered node, ignore it";
    private static final String ERR_DETECTED = "detected";
    private static final String ERR_VANISHED = "vanished";

    private static final int DPID_BEGIN = 3;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceAdminService deviceAdminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OvsdbController ovsdbController;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeService nodeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeAdminService nodeAdminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InstanceService instanceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipelineService;

    private final ExecutorService eventExecutor = newSingleThreadExecutor(
            groupedThreads(this.getClass().getSimpleName(), "event-handler", log));
    private final NetworkConfigListener configListener = new InternalConfigListener();
    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final DeviceHandler ovsdbHandler = new OvsdbDeviceHandler();
    private final DeviceHandler intgBridgeHandler = new IntegrationBridgeDeviceHandler();
    private final CordVtnNodeListener nodeListener = new InternalCordVtnNodeListener();

    private ApplicationId appId;
    private NodeId localNodeId;
    private List<ControllerInfo> controllers = ImmutableList.of();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(CORDVTN_APP_ID);
        leadershipService.runForLeadership(appId.name());
        localNodeId = clusterService.getLocalNode().id();

        configService.addListener(configListener);
        deviceService.addListener(deviceListener);
        nodeService.addListener(nodeListener);

        readControllers();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        nodeService.removeListener(nodeListener);
        deviceService.removeListener(deviceListener);
        configService.removeListener(configListener);

        leadershipService.withdraw(appId.name());
        eventExecutor.shutdown();

        log.info("Stopped");
    }

    @Override
    public void processInitState(CordVtnNode node) {
        if (!isOvsdbConnected(node)) {
            ovsdbController.connect(node.hostManagementIp().ip(), node.ovsdbPort());
            return;
        }
        createIntegrationBridge(node);
    }

    @Override
    public void processDeviceCreatedState(CordVtnNode node) {
        if (!isOvsdbConnected(node)) {
            ovsdbController.connect(node.hostManagementIp().ip(), node.ovsdbPort());
            return;
        }
        createTunnelInterface(node);
        addSystemInterface(node, node.dataInterface());
        if (node.hostManagementInterface() != null) {
            addSystemInterface(node, node.hostManagementInterface());
        }
    }

    @Override
    public void processPortCreatedState(CordVtnNode node) {
        configureInterface(node);
    }

    @Override
    public void processCompleteState(CordVtnNode node) {
        OvsdbClientService ovsdbClient = ovsdbController.getOvsdbClient(
                new OvsdbNodeId(
                        node.hostManagementIp().ip(),
                        node.ovsdbPort().toInt()));
        if (ovsdbClient != null && ovsdbClient.isConnected()) {
            ovsdbClient.disconnect();
        }
        // TODO fix postInit to be done in the proper services
        postInit(node);
        log.info("Finished init {}", node.hostname());
    }

    private boolean isOvsdbConnected(CordVtnNode node) {
        OvsdbClientService ovsdbClient = ovsdbController.getOvsdbClient(
                new OvsdbNodeId(
                        node.hostManagementIp().ip(),
                        node.ovsdbPort().toInt()));
        return deviceService.isAvailable(node.ovsdbId()) && ovsdbClient != null &&
                ovsdbClient.isConnected();
    }

    private void createIntegrationBridge(CordVtnNode node) {
        Device device = deviceService.getDevice(node.ovsdbId());
        if (device == null || !device.is(BridgeConfig.class)) {
            log.error("Failed to create integration bridge on {}", node.ovsdbId());
            return;
        }
        String dpid = node.integrationBridgeId().toString().substring(DPID_BEGIN);
        BridgeDescription bridgeDesc = DefaultBridgeDescription.builder()
                .name(INTEGRATION_BRIDGE)
                .failMode(BridgeDescription.FailMode.SECURE)
                .datapathId(dpid)
                .disableInBand()
                .controllers(controllers)
                .build();

        BridgeConfig bridgeConfig = device.as(BridgeConfig.class);
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
            log.error("Interface {} is not available on {}", ifaceName, node.hostname());
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

    private void configureInterface(CordVtnNode node) {
        Session session = connect(node.sshInfo());
        if (session == null) {
            log.error("Failed to SSH to {}", node.hostname());
            return;
        }
        getCurrentIps(session, INTEGRATION_BRIDGE).stream()
                .filter(ip -> !ip.equals(node.localManagementIp().ip()))
                .filter(ip -> !ip.equals(node.dataIp().ip()))
                .forEach(ip -> deleteIp(session, ip, INTEGRATION_BRIDGE));

        final boolean result = flushIp(session, node.dataInterface()) &&
                setInterfaceUp(session, node.dataInterface()) &&
                addIp(session, node.dataIp(), INTEGRATION_BRIDGE) &&
                addIp(session, node.localManagementIp(), INTEGRATION_BRIDGE) &&
                setInterfaceUp(session, INTEGRATION_BRIDGE);

        disconnect(session);
        if (result) {
            bootstrapNode(node);
        }
    }

    private void postInit(CordVtnNode node) {
        // TODO move the below line to DefaultCordVtnPipeline
        pipelineService.initPipeline(node);

        // TODO move the logic below to InstanceManager
        // adds existing instances to the host list
        deviceService.getPorts(node.integrationBridgeId()).stream()
                .filter(port -> port.isEnabled() &&
                        !port.number().equals(PortNumber.LOCAL) &&
                        !node.systemInterfaces().contains(port.annotations().value(PORT_NAME)))
                .forEach(port -> instanceService.addInstance(new ConnectPoint(
                        port.element().id(),
                        port.number()
                )));
        // removes stale instances from the host list
        hostService.getHosts().forEach(host -> {
            if (deviceService.getPort(
                    host.location().deviceId(),
                    host.location().port()) == null) {
                instanceService.removeInstance(host.location());
            }
        });
    }

    private class OvsdbDeviceHandler implements DeviceHandler {

        @Override
        public void connected(Device device) {
            CordVtnNode node = nodeService.node(device.id());
            if (node != null) {
                bootstrapNode(node);
            }
        }

        @Override
        public void disconnected(Device device) {
            CordVtnNode node = nodeService.node(device.id());
            if (node != null && node.state() == COMPLETE) {
                log.debug("Device({}) from {} disconnected", device.id(), node.hostname());
                deviceAdminService.removeDevice(device.id());
            }
        }
    }

    private class IntegrationBridgeDeviceHandler implements DeviceHandler {

        @Override
        public void connected(Device device) {
            CordVtnNode node = nodeService.node(device.id());
            if (node != null) {
                bootstrapNode(node);
            }
        }

        @Override
        public void disconnected(Device device) {
            CordVtnNode node = nodeService.node(device.id());
            if (node != null) {
                log.warn("Device({}) from {} disconnected", device.id(), node.hostname());
                setState(node, INIT);
            }
        }

        @Override
        public void portAdded(Port port) {
            CordVtnNode node = nodeService.node((DeviceId) port.element().id());
            String portName = port.annotations().value(PORT_NAME);
            if (node == null) {
                log.warn(format(ERR_UNREGISTERED, portName, ERR_DETECTED));
                return;
            }
            if (node.systemInterfaces().contains(portName)) {
                if (node.state() == DEVICE_CREATED) {
                    bootstrapNode(node);
                }
            } else if (node.state() == COMPLETE) {
                // TODO move this logic to InstanceManager
                instanceService.addInstance(new ConnectPoint(port.element().id(),
                        port.number()));
            } else {
                log.warn(format(ERR_INCOMPLETE, portName, ERR_DETECTED));
            }
        }

        @Override
        public void portRemoved(Port port) {
            CordVtnNode node = nodeService.node((DeviceId) port.element().id());
            String portName = port.annotations().value(PORT_NAME);
            if (node == null) {
                log.warn(format(ERR_UNREGISTERED, portName, ERR_VANISHED));
                return;
            }
            if (node.systemInterfaces().contains(portName)) {
                if (node.state() == PORT_CREATED || node.state() == COMPLETE) {
                    // always falls back to INIT state to avoid a mess caused by
                    // the multiple events received out of order
                    setState(node, INIT);
                }
            } else if (node.state() == COMPLETE) {
                // TODO move this logic to InstanceManager
                instanceService.removeInstance(new ConnectPoint(port.element().id(),
                        port.number()));
            } else {
                log.warn(format(ERR_INCOMPLETE, portName, ERR_VANISHED));
            }
        }
    }

    private class InternalDeviceListener implements DeviceListener {

        @Override
        public void event(DeviceEvent event) {
            eventExecutor.execute(() -> {
                NodeId leader = leadershipService.getLeader(appId.name());
                if (!Objects.equals(localNodeId, leader)) {
                    // do not allow to proceed without leadership
                    return;
                }
                handle(event);
            });
        }

        private void handle(DeviceEvent event) {
            DeviceHandler handler = event.subject().type().equals(SWITCH) ?
                    intgBridgeHandler : ovsdbHandler;
            Device device = event.subject();

            switch (event.type()) {
                case DEVICE_AVAILABILITY_CHANGED:
                case DEVICE_ADDED:
                    if (deviceService.isAvailable(device.id())) {
                        log.debug("Device {} is connected", device.id());
                        handler.connected(device);
                    } else {
                        log.debug("Device {} is disconnected", device.id());
                        handler.disconnected(device);
                    }
                    break;
                case PORT_ADDED:
                    log.debug("Port {} is added to {}",
                            event.port().annotations().value(PORT_NAME),
                            device.id());
                    handler.portAdded(event.port());
                    break;
                case PORT_UPDATED:
                    if (event.port().isEnabled()) {
                        log.debug("Port {} is added to {}",
                                event.port().annotations().value(PORT_NAME),
                                device.id());
                        handler.portAdded(event.port());
                    } else {
                        log.debug("Port {} is removed from {}",
                                event.port().annotations().value(PORT_NAME),
                                device.id());
                        handler.portRemoved(event.port());
                    }
                    break;
                case PORT_REMOVED:
                    log.debug("Port {} is removed from {}",
                            event.port().annotations().value(PORT_NAME),
                            device.id());
                    handler.portRemoved(event.port());
                    break;
                default:
                    break;
            }
        }
    }

    private Set<String> activePorts(DeviceId deviceId) {
        Set<String> activePorts = deviceService.getPorts(deviceId).stream()
                .filter(Port::isEnabled)
                .map(port -> port.annotations().value(PORT_NAME))
                .collect(Collectors.toSet());
        return ImmutableSet.copyOf(activePorts);
    }

    private boolean isIpAddressSet(CordVtnNode node) {
        Session session = connect(node.sshInfo());
        if (session == null) {
            log.warn("Failed to SSH to {}", node.hostname());
            return false;
        }
        Set<IpAddress> intBrIps = getCurrentIps(session, INTEGRATION_BRIDGE);
        boolean result = getCurrentIps(session, node.dataInterface()).isEmpty() &&
                isInterfaceUp(session, node.dataInterface()) &&
                intBrIps.contains(node.dataIp().ip()) &&
                intBrIps.contains(node.localManagementIp().ip()) &&
                isInterfaceUp(session, INTEGRATION_BRIDGE);

        disconnect(session);
        return result;
    }

    private boolean isCurrentStateDone(CordVtnNode node) {
        switch (node.state()) {
            case INIT:
                return deviceService.isAvailable(node.integrationBridgeId());
            case DEVICE_CREATED:
                Set<String> activePorts = activePorts(node.integrationBridgeId());
                return node.systemInterfaces().stream().allMatch(activePorts::contains);
            case PORT_CREATED:
                return isIpAddressSet(node);
            case COMPLETE:
                return false;
            default:
                return false;
        }
    }

    private void setState(CordVtnNode node, CordVtnNodeState newState) {
        if (node.state() == newState) {
            return;
        }
        CordVtnNode updated = updatedState(node, newState);
        nodeAdminService.updateNode(updated);
        log.info("Changed {} state: {}", node.hostname(), newState);
    }

    private void bootstrapNode(CordVtnNode node) {
        if (isCurrentStateDone(node)) {
            setState(node, node.state().nextState());
        } else {
            node.state().process(this, node);
        }
    }

    private class InternalCordVtnNodeListener implements CordVtnNodeListener {

        @Override
        public void event(CordVtnNodeEvent event) {
            eventExecutor.execute(() -> {
                NodeId leader = leadershipService.getLeader(appId.name());
                if (!Objects.equals(localNodeId, leader)) {
                    // do not allow to proceed without leadership
                    return;
                }
                handle(event);
            });
        }

        private void handle(CordVtnNodeEvent event) {
            switch (event.type()) {
                case NODE_CREATED:
                case NODE_UPDATED:
                    bootstrapNode(event.subject());
                    break;
                case NODE_REMOVED:
                case NODE_COMPLETE:
                case NODE_INCOMPLETE:
                default:
                    // do nothing
                    break;
            }
        }
    }

    private void readControllers() {
        CordVtnConfig config = configService.getConfig(appId, CordVtnConfig.class);
        if (config == null) {
            return;
        }
        controllers = config.controllers();
        controllers.forEach(ctrl -> {
            log.debug("Added controller {}:{}", ctrl.ip(), ctrl.port());
        });
    }

    private class InternalConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            if (!event.configClass().equals(CordVtnConfig.class)) {
                return;
            }

            switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                    readControllers();
                    break;
                default:
                    break;
            }
        }
    }
}
