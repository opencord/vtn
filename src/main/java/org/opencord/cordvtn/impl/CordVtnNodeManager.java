/*
 * Copyright 2016-present Open Networking Foundation
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
import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.ListenerRegistry;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigService;
import org.opencord.cordvtn.api.CordVtnConfig;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeAdminService;
import org.opencord.cordvtn.api.node.CordVtnNodeEvent;
import org.opencord.cordvtn.api.node.CordVtnNodeListener;
import org.opencord.cordvtn.api.node.CordVtnNodeService;
import org.opencord.cordvtn.api.node.CordVtnNodeStore;
import org.opencord.cordvtn.api.node.CordVtnNodeStoreDelegate;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.cordvtn.api.Constants.*;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manages the inventory of the cordvtn nodes provided via network configuration.
 */
@Component(immediate = true)
@Service
public class CordVtnNodeManager extends ListenerRegistry<CordVtnNodeEvent, CordVtnNodeListener>
        implements CordVtnNodeAdminService, CordVtnNodeService {

    protected final Logger log = getLogger(getClass());

    private static final String MSG_NODE  = "Node %s %s";
    private static final String MSG_CREATED = "created";
    private static final String MSG_UPDATED = "updated";
    private static final String MSG_REMOVED = "removed";

    private static final String ERR_NULL_NODE = "CordVtn node cannot be null";
    private static final String ERR_NULL_HOSTNAME = "CordVtn node hostname cannot be null";
    private static final String ERR_NULL_DEVICE_ID = "Device ID cannot be null";
    private static final String ERR_NOT_FOUND = "does not exist";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeStore nodeStore;

    private final ExecutorService eventExecutor = newSingleThreadExecutor(
            groupedThreads(this.getClass().getSimpleName(), "event-handler", log));

    private final NetworkConfigListener configListener = new InternalConfigListener();
    private final CordVtnNodeStoreDelegate delegate = new InternalCordVtnNodeStoreDelegate();

    private ApplicationId appId;
    private NodeId localNodeId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(CORDVTN_APP_ID);
        leadershipService.runForLeadership(appId.name());
        localNodeId = clusterService.getLocalNode().id();

        nodeStore.setDelegate(delegate);
        configService.addListener(configListener);

        readNodes();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        configService.removeListener(configListener);
        nodeStore.unsetDelegate(delegate);

        leadershipService.withdraw(appId.name());
        eventExecutor.shutdown();

        log.info("Stopped");
    }

    @Override
    public void createNode(CordVtnNode node) {
        checkNotNull(node, ERR_NULL_NODE);
        nodeStore.createNode(node);
        log.info(format(MSG_NODE, node.hostname(), MSG_CREATED));
    }

    @Override
    public void updateNode(CordVtnNode node) {
        checkNotNull(node, ERR_NULL_NODE);
        nodeStore.updateNode(node);
        log.debug(format(MSG_NODE, node.hostname(), MSG_UPDATED));
    }

    @Override
    public CordVtnNode removeNode(String hostname) {
        checkArgument(!Strings.isNullOrEmpty(hostname), ERR_NULL_HOSTNAME);
        CordVtnNode removed = nodeStore.removeNode(hostname);
        if (removed == null) {
            log.warn(format(MSG_NODE, hostname, ERR_NOT_FOUND));
            return null;
        }
        log.info(format(MSG_NODE, hostname, MSG_REMOVED));
        return removed;
    }

    @Override
    public Set<CordVtnNode> nodes() {
        return nodeStore.nodes();
    }

    @Override
    public Set<CordVtnNode> completeNodes() {
        // the state saved in nodeStore can be wrong if IP address settings are changed
        // after the node init has been completed since there's no way to detect it
        Set<CordVtnNode> nodes = nodes().stream()
                .filter(node -> node.state() == COMPLETE)
                .collect(Collectors.toSet());
        return ImmutableSet.copyOf(nodes);
    }

    @Override
    public CordVtnNode node(String hostname) {
        checkArgument(!Strings.isNullOrEmpty(hostname), ERR_NULL_HOSTNAME);
        return nodeStore.node(hostname);
    }

    @Override
    public CordVtnNode node(DeviceId deviceId) {
        checkNotNull(deviceId, ERR_NULL_DEVICE_ID);
        return nodes().stream()
                .filter(node -> node.integrationBridgeId().equals(deviceId) ||
                        node.ovsdbId().equals(deviceId))
                .findAny().orElse(null);
    }

    /**
     * Reads cordvtn nodes from config file.
     */
    private void readNodes() {
        NodeId leaderNodeId = leadershipService.getLeader(appId.name());
        if (!Objects.equals(localNodeId, leaderNodeId)) {
            // do not allow to proceed without leadership
            return;
        }

        CordVtnConfig config = configService.getConfig(appId, CordVtnConfig.class);
        if (config == null) {
            return;
        }
        config.cordVtnNodes().forEach(node -> {
            log.info("Read node from network config: {}", node.hostname());
            CordVtnNode existing = node(node.hostname());
            if (existing == null) {
                createNode(node);
            } else if (!existing.equals(node)) {
                // FIXME maybe we need to re-check node states
                updateNode(node);
            }
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
                    eventExecutor.execute(CordVtnNodeManager.this::readNodes);
                    break;
                default:
                    break;
            }
        }
    }

    private class InternalCordVtnNodeStoreDelegate implements CordVtnNodeStoreDelegate {

        @Override
        public void notify(CordVtnNodeEvent event) {
            if (event != null) {
                process(event);
            }
        }
    }
}
