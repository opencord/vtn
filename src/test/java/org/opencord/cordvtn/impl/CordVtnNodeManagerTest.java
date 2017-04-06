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
package org.opencord.cordvtn.impl;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onlab.junit.TestTools;
import org.onlab.junit.TestUtils;
import org.onosproject.cluster.ClusterServiceAdapter;
import org.onosproject.cluster.LeadershipServiceAdapter;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.event.Event;
import org.onosproject.net.Device;
import org.onosproject.net.config.NetworkConfigServiceAdapter;
import org.onosproject.store.service.TestStorageService;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeEvent;
import org.opencord.cordvtn.api.node.CordVtnNodeListener;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opencord.cordvtn.api.node.CordVtnNodeEvent.Type.*;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.COMPLETE;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.INIT;
import static org.opencord.cordvtn.impl.DefaultCordVtnNode.updatedState;

/**
 * Unit tests for {@link CordVtnNodeManager}.
 */
public class CordVtnNodeManagerTest extends CordVtnNodeTest {

    private static final ApplicationId TEST_APP_ID = new DefaultApplicationId(1, "test");
    private static final String ERR_SIZE = "Number of nodes did not match";
    private static final String ERR_NODE = "Node did not match";
    private static final String ERR_NOT_FOUND = "Node did not exist";
    private static final String ERR_STATE = "Node state did not match";

    private static final String HOSTNAME_1 = "node-01";
    private static final String HOSTNAME_2 = "node-02";
    private static final String HOSTNAME_3 = "node-03";

    private static final Device OF_DEVICE_1 = createDevice(1);
    private static final Device OF_DEVICE_2 = createDevice(2);
    private static final Device OF_DEVICE_3 = createDevice(3);

    private static final CordVtnNode NODE_1 = createNode(HOSTNAME_1, OF_DEVICE_1, INIT);
    private static final CordVtnNode NODE_2 = createNode(HOSTNAME_2, OF_DEVICE_2, INIT);
    private static final CordVtnNode NODE_3 = createNode(HOSTNAME_3, OF_DEVICE_3, COMPLETE);

    private final TestCordVtnNodeListener testListener = new TestCordVtnNodeListener();

    private CordVtnNodeManager target;
    private DistributedCordVtnNodeStore nodeStore;

    @Before
    public void setUp() throws Exception {
        nodeStore = new DistributedCordVtnNodeStore();
        TestUtils.setField(nodeStore, "coreService", new TestCoreService());
        TestUtils.setField(nodeStore, "storageService", new TestStorageService());
        nodeStore.activate();

        nodeStore.createNode(NODE_2);
        nodeStore.createNode(NODE_3);

        target = new CordVtnNodeManager();
        target.coreService = new TestCoreService();
        target.leadershipService = new TestLeadershipService();
        target.configService = new TestConfigService();
        target.clusterService = new TestClusterService();
        target.nodeStore = nodeStore;

        target.activate();
        target.addListener(testListener);
        clearEvents();
    }

    @After
    public void tearDown() {
        target.removeListener(testListener);
        nodeStore.deactivate();
        target.deactivate();
        nodeStore = null;
        target = null;
    }

    /**
     * Checks getting nodes returns correct set of the existing nodes.
     */
    @Test
    public void testGetNodes() {
        assertEquals(ERR_SIZE, 2, target.nodes().size());
        assertTrue(ERR_NOT_FOUND, target.nodes().contains(NODE_2));
        assertTrue(ERR_NOT_FOUND, target.nodes().contains(NODE_3));
    }

    /**
     * Checks getting complete nodes method returns correct set of the
     * existing complete nodes.
     */
    @Test
    public void testGetCompleteNodes() {
        assertEquals(ERR_SIZE, 1, target.completeNodes().size());
        assertTrue(ERR_NOT_FOUND, target.completeNodes().contains(NODE_3));
    }

    /**
     * Checks if getting node by hostname returns correct node.
     */
    @Test
    public void testGetNodeByHostname() {
        CordVtnNode node = target.node(HOSTNAME_2);
        assertEquals(ERR_NODE, NODE_2, node);
    }

    /**
     * Checks if getting node by device ID returns correct node.
     */
    @Test
    public void testGetNodeByDeviceId() {
        CordVtnNode node = target.node(OF_DEVICE_2.id());
        assertEquals(ERR_NODE, NODE_2, node);
    }

    /**
     * Checks if node creation with null fails with exception.
     */
    @Test(expected = NullPointerException.class)
    public void testCreateNullNode() {
        target.createNode(null);
    }

    /**
     * Checks if node removal with null hostname fails with exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemoveNullHostname() {
        target.removeNode(null);
    }

    /**
     * Checks if node update with null fails with exception.
     */
    @Test(expected = NullPointerException.class)
    public void testUpdateNullNode() {
        target.updateNode(null);
    }

    /**
     * Checks if duplicate node creation fails with exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateDuplicateNode() {
        target.createNode(NODE_1);
        target.createNode(NODE_1);
    }

    /**
     * Checks if unregistered node update fails with exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUpdateUnregisteredNode() {
        target.updateNode(NODE_1);
    }

    /**
     * Checks the basic node creation and removal.
     */
    @Test
    public void testCreateAndRemoveNode() {
        target.createNode(NODE_1);
        assertEquals(ERR_SIZE, 3, target.nodes().size());
        assertTrue(target.node(OF_DEVICE_1.id()) != null);

        target.removeNode(HOSTNAME_1);
        assertEquals(ERR_SIZE, 2, target.nodes().size());
        assertTrue(target.node(OF_DEVICE_1.id()) == null);

        validateEvents(NODE_CREATED, NODE_REMOVED);
    }

    /**
     * Checks if node complete event is triggered when the node state changes
     * to COMPLETE.
     */
    @Test
    public void testUpdateNodeStateComplete() {
        target.updateNode(updatedState(NODE_2, COMPLETE));
        CordVtnNode node = target.node(HOSTNAME_2);
        assertEquals(ERR_STATE, COMPLETE, node.state());

        validateEvents(NODE_UPDATED, NODE_COMPLETE);
    }

    /**
     * Checks if the node incomplete event is triggered when the node state
     * falls back from COMPLETE to INIT.
     */
    @Test
    public void testUpdateNodeStateIncomplete() {
        target.updateNode(updatedState(NODE_3, INIT));
        CordVtnNode node = target.node(HOSTNAME_3);
        assertEquals(ERR_STATE, INIT, node.state());

        validateEvents(NODE_UPDATED, NODE_INCOMPLETE);
    }

    private void clearEvents() {
        TestTools.delay(100);
        testListener.events.clear();
    }

    private void validateEvents(Enum... types) {
        TestTools.assertAfter(100, () -> {
            int i = 0;
            assertEquals("Number of events did not match", types.length, testListener.events.size());
            for (Event event : testListener.events) {
                assertEquals("Incorrect event received", types[i], event.type());
                i++;
            }
            testListener.events.clear();
        });
    }

    private static class TestCordVtnNodeListener implements CordVtnNodeListener {
        private List<CordVtnNodeEvent> events = Lists.newArrayList();

        @Override
        public void event(CordVtnNodeEvent event) {
            events.add(event);
        }
    }

    private static class TestCoreService extends CoreServiceAdapter {

        @Override
        public ApplicationId registerApplication(String name) {
            return TEST_APP_ID;
        }
    }

    private class TestConfigService extends NetworkConfigServiceAdapter {

    }

    private class TestClusterService extends ClusterServiceAdapter {

    }

    private static class TestLeadershipService extends LeadershipServiceAdapter {

    }
}
