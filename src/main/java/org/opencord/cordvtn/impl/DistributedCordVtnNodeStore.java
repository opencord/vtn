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
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.store.AbstractStore;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.MapEvent;
import org.onosproject.store.service.MapEventListener;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.Versioned;
import org.opencord.cordvtn.api.net.CidrAddr;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeEvent;
import org.opencord.cordvtn.api.node.CordVtnNodeHandler;
import org.opencord.cordvtn.api.node.CordVtnNodeState;
import org.opencord.cordvtn.api.node.CordVtnNodeStore;
import org.opencord.cordvtn.api.node.CordVtnNodeStoreDelegate;
import org.opencord.cordvtn.api.node.SshAccessInfo;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.cordvtn.api.Constants.CORDVTN_APP_ID;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.COMPLETE;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manages the inventory of cordvtn nodes using a {@link ConsistentMap}.
 */
@Component(immediate = true)
@Service
public class DistributedCordVtnNodeStore extends AbstractStore<CordVtnNodeEvent, CordVtnNodeStoreDelegate>
    implements CordVtnNodeStore {

    protected final Logger log = getLogger(getClass());

    private static final String ERR_NOT_FOUND = " does not exist";
    private static final String ERR_DUPLICATE = " already exists";

    private static final KryoNamespace SERIALIZER_CORDVTN_NODE = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(CordVtnNode.class)
            .register(DefaultCordVtnNode.class)
            .register(CidrAddr.class)
            .register(SshAccessInfo.class)
            .register(CordVtnNodeState.class)
            .register(CordVtnNodeHandler.class)
            .register(DefaultCordVtnNodeHandler.class)
            .build();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    private final ExecutorService eventExecutor = newSingleThreadExecutor(
            groupedThreads(this.getClass().getSimpleName(), "event-handler", log));
    private final MapEventListener<String, CordVtnNode> nodeStoreListener = new InternalMapListener();
    private ConsistentMap<String, CordVtnNode> nodeStore;

    @Activate
    protected void activate() {
        ApplicationId appId = coreService.registerApplication(CORDVTN_APP_ID);
        nodeStore = storageService.<String, CordVtnNode>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_CORDVTN_NODE))
                .withName("cordvtn-nodestore")
                .withApplicationId(appId)
                .build();
        nodeStore.addListener(nodeStoreListener);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        nodeStore.removeListener(nodeStoreListener);
        log.info("Stopped");
    }

    @Override
    public Set<CordVtnNode> nodes() {
        Set<CordVtnNode> nodes = nodeStore.values().stream()
                .map(Versioned::value)
                .collect(Collectors.toSet());
        return ImmutableSet.copyOf(nodes);
    }

    @Override
    public CordVtnNode node(String hostname) {
        Versioned<CordVtnNode> versioned = nodeStore.get(hostname);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public void createNode(CordVtnNode node) {
        nodeStore.compute(node.hostname(), (hostname, existing) -> {
            final String error = node.hostname() + ERR_DUPLICATE;
            checkArgument(existing == null, error);
            return node;
        });
    }

    @Override
    public void updateNode(CordVtnNode node) {
        nodeStore.compute(node.hostname(), (hostname, existing) -> {
            final String error = node.hostname() + ERR_NOT_FOUND;
            checkArgument(existing != null, error);
            return node;
        });
    }

    @Override
    public CordVtnNode removeNode(String hostname) {
        Versioned<CordVtnNode> removed = nodeStore.remove(hostname);
        return removed == null ? null : removed.value();
    }

    private class InternalMapListener implements MapEventListener<String, CordVtnNode> {

        @Override
        public void event(MapEvent<String, CordVtnNode> event) {
            switch (event.type()) {
                case INSERT:
                    log.debug("CordVtn node is created {}", event.newValue().value());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new CordVtnNodeEvent(
                                CordVtnNodeEvent.Type.NODE_CREATED,
                                event.newValue().value()
                        ));
                        if (event.newValue().value().state() == COMPLETE) {
                            notifyDelegate(new CordVtnNodeEvent(
                                    CordVtnNodeEvent.Type.NODE_COMPLETE,
                                    event.newValue().value()
                            ));
                        }
                    });
                    break;
                case UPDATE:
                    log.debug("CordVtn node is updated {}", event.newValue().value());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new CordVtnNodeEvent(
                                CordVtnNodeEvent.Type.NODE_UPDATED,
                                event.newValue().value()
                        ));
                        processUpdated(event.oldValue().value(), event.newValue().value());
                    });
                    break;
                case REMOVE:
                    log.debug("CordVtn node is removed {}", event.oldValue().value());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new CordVtnNodeEvent(
                                CordVtnNodeEvent.Type.NODE_REMOVED,
                                event.oldValue().value()
                        ));
                    });
                    break;
                default:
                    // do nothing
                    break;
            }
        }

        private void processUpdated(CordVtnNode oldNode, CordVtnNode newNode) {
            if (oldNode.state() != COMPLETE && newNode.state() == COMPLETE) {
                notifyDelegate(new CordVtnNodeEvent(
                        CordVtnNodeEvent.Type.NODE_COMPLETE,
                        newNode
                ));
            } else if (oldNode.state() == COMPLETE && newNode.state() != COMPLETE) {
                notifyDelegate(new CordVtnNodeEvent(
                        CordVtnNodeEvent.Type.NODE_INCOMPLETE,
                        newNode
                ));
            }
        }
    }
}
