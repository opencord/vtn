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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.jcraft.jsch.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onlab.junit.TestUtils;
import org.onlab.packet.ChassisId;
import org.onlab.packet.IpAddress;
import org.onosproject.cluster.ClusterServiceAdapter;
import org.onosproject.cluster.ControllerNode;
import org.onosproject.cluster.DefaultControllerNode;
import org.onosproject.cluster.LeadershipServiceAdapter;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.event.DefaultEventSinkRegistry;
import org.onosproject.event.Event;
import org.onosproject.event.EventDeliveryService;
import org.onosproject.event.EventSink;
import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Port;
import org.onosproject.net.behaviour.BridgeConfig;
import org.onosproject.net.behaviour.BridgeDescription;
import org.onosproject.net.behaviour.InterfaceConfig;
import org.onosproject.net.config.NetworkConfigServiceAdapter;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceServiceAdapter;
import org.onosproject.net.driver.Behaviour;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.host.HostDescription;
import org.onosproject.net.host.HostServiceAdapter;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.ovsdb.controller.OvsdbClientService;
import org.onosproject.ovsdb.controller.OvsdbController;
import org.opencord.cordvtn.api.core.CordVtnPipeline;
import org.opencord.cordvtn.api.core.InstanceService;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeAdminService;
import org.opencord.cordvtn.api.node.CordVtnNodeEvent;
import org.opencord.cordvtn.api.node.CordVtnNodeListener;
import org.opencord.cordvtn.api.node.CordVtnNodeService;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.onosproject.net.Device.Type.CONTROLLER;
import static org.onosproject.net.NetTestTools.injectEventDispatcher;
import static org.onosproject.net.device.DeviceEvent.Type.*;
import static org.opencord.cordvtn.api.Constants.INTEGRATION_BRIDGE;
import static org.opencord.cordvtn.api.node.CordVtnNodeEvent.Type.NODE_UPDATED;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.*;
import static org.opencord.cordvtn.impl.RemoteIpCommandUtil.*;
import static org.powermock.api.easymock.PowerMock.mockStatic;

/**
 * Unit test for CordVtnNodeHandler which provides cordvtn node bootstrap state machine.
 */
@RunWith(PowerMockRunner.class)
public class DefaultCordVtnNodeHandlerTest extends CordVtnNodeTest {

    private static final String ERR_STATE = "Node state did not match";

    private static final ApplicationId TEST_APP_ID = new DefaultApplicationId(1, "test");
    private static final NodeId LOCAL_NODE_ID = new NodeId("local");
    private static final ControllerNode LOCAL_CTRL =
            new DefaultControllerNode(LOCAL_NODE_ID, IpAddress.valueOf("127.0.0.1"));

    private static final Device OVSDB_DEVICE = new TestDevice(
            new ProviderId("of", "foo"),
            DeviceId.deviceId("ovsdb:" + TEST_CIDR_ADDR.ip().toString()),
            CONTROLLER,
            "manufacturer",
            "hwVersion",
            "swVersion",
            "serialNumber",
            new ChassisId(1));

    private static final Device OF_DEVICE_1 = createDevice(1);

    private static final Device OF_DEVICE_2 = createDevice(2);
    private static final Port OF_DEVICE_2_PORT_1 = createPort(OF_DEVICE_2, 1, TEST_DATA_IFACE);
    private static final Port OF_DEVICE_2_PORT_2 = createPort(OF_DEVICE_2, 2, TEST_VXLAN_IFACE);

    private static final Device OF_DEVICE_3 = createDevice(3);
    private static final Port OF_DEVICE_3_PORT_1 = createPort(OF_DEVICE_3, 1, TEST_DATA_IFACE);
    private static final Port OF_DEVICE_3_PORT_2 = createPort(OF_DEVICE_3, 2, TEST_VXLAN_IFACE);

    private static final Device OF_DEVICE_4 = createDevice(4);
    private static final Port OF_DEVICE_4_PORT_1 = createPort(OF_DEVICE_4, 1, TEST_DATA_IFACE);
    private static final Port OF_DEVICE_4_PORT_2 = createPort(OF_DEVICE_4, 2, TEST_VXLAN_IFACE);

    private static final CordVtnNode NODE_1 = createNode("node-01", OF_DEVICE_1, INIT);
    private static final CordVtnNode NODE_2 = createNode("node-02", OF_DEVICE_2, DEVICE_CREATED);
    private static final CordVtnNode NODE_3 = createNode("node-03", OF_DEVICE_3, PORT_CREATED);
    private static final CordVtnNode NODE_4 = createNode("node-04", OF_DEVICE_4, COMPLETE);

    private TestDeviceService deviceService;
    private TestNodeManager nodeManager;
    private DefaultCordVtnNodeHandler target;

    @Before
    public void setUp() throws Exception {
        this.deviceService = new TestDeviceService();
        this.nodeManager = new TestNodeManager();

        // add fake ovsdb device
        this.deviceService.devMap.put(OVSDB_DEVICE.id(), OVSDB_DEVICE);

        // add fake OF devices
        this.deviceService.devMap.put(OF_DEVICE_1.id(), OF_DEVICE_1);
        this.deviceService.devMap.put(OF_DEVICE_2.id(), OF_DEVICE_2);
        this.deviceService.devMap.put(OF_DEVICE_3.id(), OF_DEVICE_3);
        this.deviceService.devMap.put(OF_DEVICE_4.id(), OF_DEVICE_4);

        // add fake OF ports
        this.deviceService.portList.add(OF_DEVICE_3_PORT_1);
        this.deviceService.portList.add(OF_DEVICE_3_PORT_2);
        this.deviceService.portList.add(OF_DEVICE_4_PORT_1);
        this.deviceService.portList.add(OF_DEVICE_4_PORT_2);

        // add fake nodes
        this.nodeManager.nodeMap.put(OF_DEVICE_1.id(), NODE_1);
        this.nodeManager.nodeMap.put(OF_DEVICE_2.id(), NODE_2);
        this.nodeManager.nodeMap.put(OF_DEVICE_3.id(), NODE_3);
        this.nodeManager.nodeMap.put(OF_DEVICE_4.id(), NODE_4);

        OvsdbClientService mockOvsdbClient = createMock(OvsdbClientService.class);
        expect(mockOvsdbClient.isConnected())
                .andReturn(true)
                .anyTimes();
        replay(mockOvsdbClient);

        OvsdbController mockOvsdbController = createMock(OvsdbController.class);
        expect(mockOvsdbController.getOvsdbClient(anyObject()))
                .andReturn(mockOvsdbClient)
                .anyTimes();
        replay(mockOvsdbController);

        DeviceAdminService mockDeviceAdminService = createMock(DeviceAdminService.class);
        mockDeviceAdminService.removeDevice(anyObject());
        replay(mockDeviceAdminService);

        target = new DefaultCordVtnNodeHandler();
        target.coreService = new TestCoreService();
        target.leadershipService = new TestLeadershipService();
        target.clusterService = new TestClusterService();
        target.configService = new TestConfigService();
        target.deviceService = this.deviceService;
        target.deviceAdminService = mockDeviceAdminService;
        target.hostService = new TestHostService();
        target.ovsdbController = mockOvsdbController;
        target.nodeService = this.nodeManager;
        target.nodeAdminService = this.nodeManager;
        target.instanceService = new TestInstanceService();
        target.pipelineService = new TestCordVtnPipeline();
        TestUtils.setField(target, "eventExecutor", MoreExecutors.newDirectExecutorService());
        injectEventDispatcher(target, new TestEventDispatcher());
        target.activate();
    }

    @After
    public void tearDown() {
        target.deactivate();
        deviceService = null;
        nodeManager = null;
        target = null;
    }

    /**
     * Checks if the node state changes from INIT to DEVICE_CREATED when
     * the integration bridge created.
     */
    @Test
    public void testProcessInitState() {
        deviceService.addDevice(OF_DEVICE_1);
        CordVtnNode current = nodeManager.node(NODE_1.integrationBridgeId());
        assertEquals(ERR_STATE, DEVICE_CREATED, current.state());
    }

    /**
     * Checks if the node state changes from DEVICE_CREATED to PORT_CREATED
     * when the data interface and vxlan interface are added.
     */
    @Test
    public void testProcessDeviceCreatedState() {
        CordVtnNode current = nodeManager.node(NODE_2.integrationBridgeId());
        assertEquals(ERR_STATE, DEVICE_CREATED, current.state());

        // Add the data port and check if the state is still in DEVICE_CREATED
        deviceService.addPort(OF_DEVICE_2, OF_DEVICE_2_PORT_1);
        current = nodeManager.node(NODE_2.integrationBridgeId());
        assertEquals(ERR_STATE, DEVICE_CREATED, current.state());

        // Add the vxlan port and check if the state changes to PORT_CREATED
        deviceService.addPort(OF_DEVICE_2, OF_DEVICE_2_PORT_2);
        current = nodeManager.node(NODE_2.integrationBridgeId());
        assertEquals(ERR_STATE, PORT_CREATED, current.state());
    }

    /**
     * Checks if the node state changes to COMPLETE when the network interface
     * configuration is done.
     */
    @PrepareForTest(RemoteIpCommandUtil.class)
    @Test
    public void testProcessPortCreatedState() {
        Session mockSession = createMock(Session.class);
        mockStatic(RemoteIpCommandUtil.class);
        expect(connect(anyObject())).andReturn(mockSession);
        expect(getCurrentIps(mockSession, INTEGRATION_BRIDGE)).andReturn(Sets.newHashSet(TEST_CIDR_ADDR.ip()));
        expect(getCurrentIps(mockSession, TEST_DATA_IFACE)).andReturn(Sets.newHashSet());
        expect(isInterfaceUp(anyObject(), anyObject())).andReturn(true).anyTimes();
        RemoteIpCommandUtil.disconnect(anyObject());
        PowerMock.replay(RemoteIpCommandUtil.class);

        // There's no events for IP address changes on the interfaces, so just
        // Set node state updated to trigger node bootstrap
        nodeManager.updateNodeAndMakeEvent(NODE_3);
        CordVtnNode current = nodeManager.node(NODE_3.integrationBridgeId());
        assertEquals(ERR_STATE, COMPLETE, current.state());
    }

    /**
     * Checks if the node state falls back to INIT when the integration bridge
     * is removed.
     */
    @Test
    public void testBackToInitStateWhenDeviceRemoved() {
        // Remove the device from DEVICE_CREATED state node
        deviceService.removeDevice(OF_DEVICE_2);
        CordVtnNode current = nodeManager.node(NODE_2.integrationBridgeId());
        assertEquals(ERR_STATE, INIT, current.state());

        // Remove the device from PORT_CREATED state node
        deviceService.removeDevice(OF_DEVICE_3);
        current = nodeManager.node(NODE_3.integrationBridgeId());
        assertEquals(ERR_STATE, INIT, current.state());

        // Remove the device from COMPLETE state node
        deviceService.removeDevice(OF_DEVICE_4);
        current = nodeManager.node(NODE_4.integrationBridgeId());
        assertEquals(ERR_STATE, INIT, current.state());
    }

    /**
     * Checks if the node state falls back to INIT state when the ports
     * are removed.
     */
    @Test
    public void testBackToDeviceCreatedStateWhenPortRemoved() {
        // Remove the device from PORT_CREATED state node
        deviceService.removePort(OF_DEVICE_3, OF_DEVICE_3_PORT_1);
        CordVtnNode current = nodeManager.node(NODE_3.integrationBridgeId());
        assertEquals(ERR_STATE, INIT, current.state());

        // Remove the device from COMPLETE state node
        deviceService.removePort(OF_DEVICE_4, OF_DEVICE_4_PORT_1);
        current = nodeManager.node(NODE_4.integrationBridgeId());
        assertEquals(ERR_STATE, INIT, current.state());
    }

    /**
     * Checks if the node state falls back to PORT_CREATED when the interface
     * configurations are incomplete.
     */
    @PrepareForTest(RemoteIpCommandUtil.class)
    @Test
    public void testBackToPortCreatedStateWhenIpRemoved() {
        // Mocks SSH connection failure
        mockStatic(RemoteIpCommandUtil.class);
        expect(connect(anyObject())).andReturn(null);
        PowerMock.replay(RemoteIpCommandUtil.class);

        // ONOS is not able to detect IP is removed from the interface
        // Just triggers node update event for the PORT_CREATED node and
        // check if it stays in PORT_CREATED state
        nodeManager.updateNode(NODE_3);
        CordVtnNode current = nodeManager.node(NODE_3.integrationBridgeId());
        assertEquals(ERR_STATE, PORT_CREATED, current.state());
    }

    private static final class TestDevice extends DefaultDevice {
        InterfaceConfig mockInterfaceConfig = createMock(InterfaceConfig.class);
        BridgeConfig mockBridgeConfig = createMock(BridgeConfig.class);

        private TestDevice(ProviderId providerId,
                   DeviceId id,
                   Type type,
                   String manufacturer,
                   String hwVersion,
                   String swVersion,
                   String serialNumber,
                   ChassisId chassisId,
                   Annotations... annotations) {
            super(providerId,
                    id,
                    type,
                    manufacturer,
                    hwVersion,
                    swVersion,
                    serialNumber,
                    chassisId,
                    annotations);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <B extends Behaviour> B as(Class<B> projectionClass) {
            if (projectionClass.equals(BridgeConfig.class)) {
                expect(this.mockBridgeConfig.addBridge((BridgeDescription) anyObject()))
                        .andReturn(true)
                        .anyTimes();
                this.mockBridgeConfig.addPort(anyObject(), anyString());
                replay(mockBridgeConfig);
                return (B) mockBridgeConfig;
            } else if (projectionClass.equals(InterfaceConfig.class)) {
                expect(this.mockInterfaceConfig.addTunnelMode(anyString(), anyObject()))
                        .andReturn(true)
                        .anyTimes();
                replay(mockInterfaceConfig);
                return (B) mockInterfaceConfig;
            } else {
                return null;
            }
        }

        @Override
        public <B extends Behaviour> boolean is(Class<B> projectionClass) {
            return true;
        }
    }


    private static class TestCoreService extends CoreServiceAdapter {

        @Override
        public ApplicationId registerApplication(String name) {
            return TEST_APP_ID;
        }
    }

    private static class TestLeadershipService extends LeadershipServiceAdapter {

        @Override
        public NodeId getLeader(String path) {
            return LOCAL_NODE_ID;
        }
    }

    private static class TestClusterService extends ClusterServiceAdapter {

        @Override
        public ControllerNode getLocalNode() {
            return LOCAL_CTRL;
        }
    }

    private static class TestConfigService extends NetworkConfigServiceAdapter {

    }

    private static class TestNodeManager implements CordVtnNodeService, CordVtnNodeAdminService {
        Map<DeviceId, CordVtnNode> nodeMap = Maps.newHashMap();
        List<CordVtnNodeListener> listeners = Lists.newArrayList();

        @Override
        public Set<CordVtnNode> nodes() {
            return ImmutableSet.copyOf(nodeMap.values());
        }

        @Override
        public Set<CordVtnNode> completeNodes() {
            return null;
        }

        @Override
        public CordVtnNode node(String hostname) {
            return null;
        }

        @Override
        public CordVtnNode node(DeviceId deviceId) {
            return nodeMap.get(deviceId);
        }

        @Override
        public void addListener(CordVtnNodeListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(CordVtnNodeListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void createNode(CordVtnNode node) {
            nodeMap.put(node.integrationBridgeId(), node);
        }

        @Override
        public void updateNode(CordVtnNode node) {
            nodeMap.put(node.integrationBridgeId(), node);
        }

        public void updateNodeAndMakeEvent(CordVtnNode node) {
            nodeMap.put(node.integrationBridgeId(), node);
            CordVtnNodeEvent event = new CordVtnNodeEvent(NODE_UPDATED, node);
            listeners.forEach(l -> l.event(event));
        }

        @Override
        public CordVtnNode removeNode(String hostname) {
            return null;
        }
    }

    private static class TestDeviceService extends DeviceServiceAdapter {
        Map<DeviceId, Device> devMap = Maps.newHashMap();
        List<Port> portList = Lists.newArrayList();
        List<DeviceListener> listeners = Lists.newArrayList();

        @Override
        public void addListener(DeviceListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(DeviceListener listener) {
            listeners.remove(listener);
        }

        @Override
        public Device getDevice(DeviceId deviceId) {
            return devMap.get(deviceId);
        }

        @Override
        public List<Port> getPorts(DeviceId deviceId) {
            return this.portList.stream()
                    .filter(p -> p.element().id().equals(deviceId))
                    .collect(Collectors.toList());
        }

        @Override
        public boolean isAvailable(DeviceId deviceId) {
            return devMap.containsKey(deviceId);
        }

        void addDevice(Device device) {
            devMap.put(device.id(), device);
            DeviceEvent event = new DeviceEvent(DEVICE_ADDED, device);
            listeners.forEach(l -> l.event(event));
        }

        void removeDevice(Device device) {
            devMap.remove(device.id());
            DeviceEvent event = new DeviceEvent(DEVICE_AVAILABILITY_CHANGED, device);
            listeners.forEach(l -> l.event(event));
        }

        void addPort(Device device, Port port) {
            portList.add(port);
            DeviceEvent event = new DeviceEvent(PORT_ADDED, device, port);
            listeners.forEach(l -> l.event(event));
        }

        void removePort(Device device, Port port) {
            portList.remove(port);
            DeviceEvent event = new DeviceEvent(PORT_REMOVED, device, port);
            listeners.forEach(l -> l.event(event));
        }
    }

    private static class TestHostService extends HostServiceAdapter {

        @Override
        public Iterable<Host> getHosts() {
            return Lists.newArrayList();
        }
    }

    private static class TestInstanceService implements InstanceService {

        @Override
        public void addInstance(ConnectPoint connectPoint) {

        }

        @Override
        public void addInstance(HostId hostId, HostDescription description) {

        }

        @Override
        public void removeInstance(ConnectPoint connectPoint) {

        }

        @Override
        public void removeInstance(HostId hostId) {

        }
    }

    private static class TestCordVtnPipeline implements CordVtnPipeline {

        @Override
        public void initPipeline(CordVtnNode node) {

        }

        @Override
        public void cleanupPipeline() {

        }

        @Override
        public void processFlowRule(boolean install, FlowRule rule) {

        }
    }

    public class TestEventDispatcher extends DefaultEventSinkRegistry
            implements EventDeliveryService {

        @Override
        @SuppressWarnings("unchecked")
        public synchronized void post(Event event) {
            EventSink sink = getSink(event.getClass());
            checkState(sink != null, "No sink for event %s", event);
            sink.process(event);
        }

        @Override
        public void setDispatchTimeLimit(long millis) {
        }

        @Override
        public long getDispatchTimeLimit() {
            return 0;
        }
    }
}
