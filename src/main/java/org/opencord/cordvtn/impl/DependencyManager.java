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
package org.opencord.cordvtn.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.KryoNamespace;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.MapEvent;
import org.onosproject.store.service.MapEventListener;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.cordvtn.api.CordVtnAdminService;
import org.opencord.cordvtn.api.CordVtnNode;
import org.opencord.cordvtn.api.Dependency;
import org.opencord.cordvtn.api.Dependency.Type;
import org.opencord.cordvtn.api.DependencyService;
import org.opencord.cordvtn.api.Instance;
import org.onosproject.core.DefaultGroupId;
import org.onosproject.core.GroupId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.group.Group;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.GroupService;
import org.opencord.cordvtn.api.NetworkId;
import org.opencord.cordvtn.api.ProviderNetwork;
import org.opencord.cordvtn.api.SegmentId;
import org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType;
import org.opencord.cordvtn.api.VtnNetwork;
import org.opencord.cordvtn.api.VtnNetworkEvent;
import org.opencord.cordvtn.api.VtnNetworkListener;
import org.opencord.cordvtn.impl.handler.AbstractInstanceHandler;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.opencord.cordvtn.api.Dependency.Type.BIDIRECTIONAL;
import static org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType.ACCESS_AGENT;
import static org.opencord.cordvtn.impl.CordVtnPipeline.*;
import static org.onosproject.net.group.DefaultGroupBucket.createSelectGroupBucket;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provisions service dependency capabilities between network services.
 */
@Component(immediate = true)
@Service
public class DependencyManager extends AbstractInstanceHandler implements DependencyService {

    protected final Logger log = getLogger(getClass());

    private static final String ERR_NET_FAIL = "Failed to get VTN network ";
    private static final String MSG_CREATE = "Created dependency %s";
    private static final String MSG_REMOVE = "Removed dependency %s";
    private static final String ADDED = "Added ";
    private static final String REMOVED = "Removed ";

    private static final KryoNamespace SERIALIZER_DEPENDENCY = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(VtnNetwork.class)
            .register(NetworkId.class)
            .register(SegmentId.class)
            .register(ServiceNetworkType.class)
            .register(ProviderNetwork.class)
            .register(Dependency.class)
            .register(Type.class)
            .build();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeManager nodeManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnAdminService vtnService;

    private final VtnNetworkListener vtnNetListener = new InternalVtnNetListener();
    private final MapEventListener<NetworkId, Set<Dependency>> dependencyListener =
            new DependencyMapListener();

    private ConsistentMap<NetworkId, Set<Dependency>> dependencyStore;
    private NodeId localNodeId;

    @Activate
    protected void activate() {
        super.activate();

        dependencyStore = storageService.<NetworkId, Set<Dependency>>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_DEPENDENCY))
                .withName("cordvtn-dependencymap")
                .withApplicationId(appId)
                .build();
        dependencyStore.addListener(dependencyListener);

        localNodeId = clusterService.getLocalNode().id();
        leadershipService.runForLeadership(appId.name());
        vtnService.addListener(vtnNetListener);
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
        dependencyStore.removeListener(dependencyListener);
        vtnService.removeListener(vtnNetListener);
        leadershipService.withdraw(appId.name());
    }

    @Override
    public void createDependency(NetworkId subNetId, NetworkId proNetId, Type type) {
        // FIXME this is not safe
        VtnNetwork existing = vtnService.vtnNetwork(subNetId);
        if (existing == null) {
            log.warn("Failed to create dependency between {} and {}", subNetId, proNetId);
            return;
        }
        vtnService.createServiceNetwork(existing);
        VtnNetwork updated = VtnNetwork.builder(existing)
                .addProvider(proNetId, type)
                .build();
        vtnService.updateServiceNetwork(updated);
    }

    @Override
    public void removeDependency(NetworkId subNetId, NetworkId proNetId) {
        // FIXME this is not safe
        VtnNetwork subNet = vtnService.vtnNetwork(subNetId);
        if (subNet == null) {
            log.warn("No dependency exists between {} and {}", subNetId, proNetId);
            return;
        }
        VtnNetwork updated = VtnNetwork.builder(subNet)
                .delProvider(proNetId)
                .build();
        vtnService.updateServiceNetwork(updated);
    }

    @Override
    public void instanceDetected(Instance instance) {
        // TODO remove this when XOS provides access agent information
        // and handle it the same way wit the other instances
        if (instance.netType() == ACCESS_AGENT) {
            return;
        }

        VtnNetwork vtnNet = vtnService.vtnNetwork(instance.netId());
        if (vtnNet == null) {
            final String error = ERR_NET_FAIL + instance.netId();
            throw new IllegalStateException(error);
        }

        if (!vtnNet.providers().isEmpty()) {
            updateSubscriberInstances(vtnNet, instance, true);
        }
        // TODO check if subscribers on this network
        updateProviderInstances(vtnNet);
    }

    @Override
    public void instanceRemoved(Instance instance) {
        // TODO remove this when XOS provides access agent information
        // and handle it the same way wit the other instances
        if (instance.netType() == ACCESS_AGENT) {
            return;
        }

        VtnNetwork vtnNet = vtnService.vtnNetwork(instance.netId());
        if (vtnNet == null) {
            final String error = ERR_NET_FAIL + instance.netId();
            throw new IllegalStateException(error);
        }

        if (!vtnNet.providers().isEmpty()) {
            updateSubscriberInstances(vtnNet, instance, false);
        }
        // TODO check if subscribers on this network
        updateProviderInstances(vtnNet);
    }

    private void dependencyCreated(Dependency dependency) {
        populateDependencyRules(dependency.subscriber(), dependency.provider(),
                                dependency.type(), true);
        log.info(String.format(MSG_CREATE, dependency));
    }

    private void dependencyRemoved(Dependency dependency) {
        populateDependencyRules(dependency.subscriber(), dependency.provider(),
                                dependency.type(), false);
        log.info(String.format(MSG_REMOVE, dependency));
    }

    private void updateProviderInstances(VtnNetwork provider) {
        Set<DeviceId> devices = nodeManager.completeNodes().stream()
                .map(CordVtnNode::integrationBridgeId)
                .collect(Collectors.toSet());

        GroupKey groupKey = getGroupKey(provider.id());
        for (DeviceId deviceId : devices) {
            Group group = groupService.getGroup(deviceId, groupKey);
            if (group == null) {
                continue;
            }

            List<GroupBucket> oldBuckets = group.buckets().buckets();
            List<GroupBucket> newBuckets = getProviderGroupBuckets(
                    deviceId,
                    provider.segmentId().id(),
                    getInstances(provider.id())).buckets();

            if (oldBuckets.equals(newBuckets)) {
                continue;
            }

            List<GroupBucket> bucketsToRemove = Lists.newArrayList(oldBuckets);
            bucketsToRemove.removeAll(newBuckets);
            if (!bucketsToRemove.isEmpty()) {
                groupService.removeBucketsFromGroup(
                        deviceId,
                        groupKey,
                        new GroupBuckets(bucketsToRemove),
                        groupKey, appId);
                log.debug("Removed buckets from provider({}) group on {}: {}",
                          provider.id(), deviceId, bucketsToRemove);
            }

            List<GroupBucket> bucketsToAdd = Lists.newArrayList(newBuckets);
            bucketsToAdd.removeAll(oldBuckets);
            if (!bucketsToAdd.isEmpty()) {
                groupService.addBucketsToGroup(
                        deviceId,
                        groupKey,
                        new GroupBuckets(bucketsToAdd),
                        groupKey, appId);
                log.debug("Added buckets to provider({}) group on {}: {}",
                          provider.id(), deviceId, bucketsToAdd);
            }
        }
    }

    private void updateSubscriberInstances(VtnNetwork subscriber, Instance instance,
                                           boolean isDetected) {
        DeviceId deviceId = instance.deviceId();
        final String isAdded = isDetected ? ADDED : REMOVED;
        subscriber.providers().stream().forEach(provider -> {
            populateInPortRule(
                    ImmutableMap.of(deviceId, ImmutableSet.of(instance.portNumber())),
                    ImmutableMap.of(deviceId, getGroupId(provider.id(), deviceId)),
                    isDetected);

            log.info(isAdded + "subscriber instance({}) for provider({})",
                     instance.host().id(), provider.id());
        });
    }

    private void populateDependencyRules(VtnNetwork subscriber,
                                         VtnNetwork provider,
                                         Type type, boolean install) {
        Map<DeviceId, GroupId> providerGroups = Maps.newHashMap();
        Map<DeviceId, Set<PortNumber>> subscriberPorts = Maps.newHashMap();

        nodeManager.completeNodes().stream().forEach(node -> {
            DeviceId deviceId = node.integrationBridgeId();
            GroupId groupId = getProviderGroup(provider, deviceId);
            providerGroups.put(deviceId, groupId);

            Set<PortNumber> ports = getInstances(subscriber.id())
                    .stream()
                    .filter(instance -> instance.deviceId().equals(deviceId))
                    .map(Instance::portNumber)
                    .collect(Collectors.toSet());
            subscriberPorts.put(deviceId, ports);
        });

        Ip4Prefix subscriberIp = subscriber.subnet().getIp4Prefix();
        Ip4Prefix providerIp = provider.subnet().getIp4Prefix();

        populateInPortRule(subscriberPorts, providerGroups, install);
        populateIndirectAccessRule(
                subscriberIp,
                provider.serviceIp().getIp4Address(),
                providerGroups,
                install);
        populateDirectAccessRule(subscriberIp, providerIp, install);
        if (type == BIDIRECTIONAL) {
            populateDirectAccessRule(providerIp, subscriberIp, install);
        }
    }

    private void populateIndirectAccessRule(Ip4Prefix srcIp, Ip4Address serviceIp,
                                            Map<DeviceId, GroupId> outGroups,
                                            boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcIp)
                .matchIPDst(serviceIp.toIpPrefix())
                .build();

        for (Map.Entry<DeviceId, GroupId> outGroup : outGroups.entrySet()) {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .group(outGroup.getValue())
                    .build();

            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(PRIORITY_HIGH)
                    .forDevice(outGroup.getKey())
                    .forTable(TABLE_ACCESS)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRule);
        }
    }

    private void populateDirectAccessRule(Ip4Prefix srcIp, Ip4Prefix dstIp, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcIp)
                .matchIPDst(dstIp)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(TABLE_DST)
                .build();

        nodeManager.completeNodes().stream().forEach(node -> {
            DeviceId deviceId = node.integrationBridgeId();
            FlowRule flowRuleDirect = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(PRIORITY_DEFAULT)
                    .forDevice(deviceId)
                    .forTable(TABLE_ACCESS)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRuleDirect);
        });
    }

    private void populateInPortRule(Map<DeviceId, Set<PortNumber>> subscriberPorts,
                                    Map<DeviceId, GroupId> providerGroups,
                                    boolean install) {
        for (Map.Entry<DeviceId, Set<PortNumber>> entry : subscriberPorts.entrySet()) {
            Set<PortNumber> ports = entry.getValue();
            DeviceId deviceId = entry.getKey();

            GroupId groupId = providerGroups.get(deviceId);
            if (groupId == null) {
                continue;
            }

            ports.stream().forEach(port -> {
                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchInPort(port)
                        .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .group(groupId)
                        .build();

                FlowRule flowRule = DefaultFlowRule.builder()
                        .fromApp(appId)
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .withPriority(PRIORITY_DEFAULT)
                        .forDevice(deviceId)
                        .forTable(TABLE_IN_SERVICE)
                        .makePermanent()
                        .build();

                pipeline.processFlowRule(install, flowRule);
            });
        }
    }

    private GroupId getGroupId(NetworkId netId, DeviceId deviceId) {
        return new DefaultGroupId(Objects.hash(netId, deviceId));
    }

    private GroupKey getGroupKey(NetworkId netId) {
        return new DefaultGroupKey(netId.id().getBytes());
    }

    private GroupId getProviderGroup(VtnNetwork provider, DeviceId deviceId) {
        GroupKey groupKey = getGroupKey(provider.id());
        Group group = groupService.getGroup(deviceId, groupKey);
        GroupId groupId = getGroupId(provider.id(), deviceId);

        if (group != null) {
            return groupId;
        }

        GroupBuckets buckets = getProviderGroupBuckets(
                deviceId, provider.segmentId().id(), getInstances(provider.id()));
        GroupDescription groupDescription = new DefaultGroupDescription(
                deviceId,
                GroupDescription.Type.SELECT,
                buckets,
                groupKey,
                groupId.id(),
                appId);

        groupService.addGroup(groupDescription);
        return groupId;
    }

    private void removeProviderGroup(NetworkId netId) {
        GroupKey groupKey = getGroupKey(netId);
        nodeManager.completeNodes().stream()
                .forEach(node -> {
                    DeviceId deviceId = node.integrationBridgeId();
                    Group group = groupService.getGroup(deviceId, groupKey);
                    if (group != null) {
                        groupService.removeGroup(deviceId, groupKey, appId);
                    }
        });
        log.debug("Removed group for network {}", netId);
    }

    private GroupBuckets getProviderGroupBuckets(DeviceId deviceId,
                                                 long tunnelId,
                                                 Set<Instance> instances) {
        List<GroupBucket> buckets = Lists.newArrayList();
        instances.stream().forEach(instance -> {
            Ip4Address tunnelIp = nodeManager.dataIp(instance.deviceId()).getIp4Address();

            if (deviceId.equals(instance.deviceId())) {
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setEthDst(instance.mac())
                        .setOutput(instance.portNumber())
                        .build();
                buckets.add(createSelectGroupBucket(treatment));
            } else {
                ExtensionTreatment tunnelDst =
                        pipeline.tunnelDstTreatment(deviceId, tunnelIp);
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setEthDst(instance.mac())
                        .extension(tunnelDst, deviceId)
                        .setTunnelId(tunnelId)
                        .setOutput(nodeManager.tunnelPort(instance.deviceId()))
                        .build();
                buckets.add(createSelectGroupBucket(treatment));
            }
        });
        return new GroupBuckets(buckets);
    }

    private class InternalVtnNetListener implements VtnNetworkListener {

        @Override
        public void event(VtnNetworkEvent event) {
            NodeId leader = leadershipService.getLeader(appId.name());
            if (!Objects.equals(localNodeId, leader)) {
                // do not allow to proceed without leadership
                return;
            }

            switch (event.type()) {
                case VTN_NETWORK_CREATED:
                case VTN_NETWORK_UPDATED:
                    log.debug("Processing dependency for {}", event.subject());
                    eventExecutor.execute(() -> updateDependency(event.subject()));
                    break;
                case VTN_NETWORK_REMOVED:
                    log.debug("Removing dependency for {}", event.subject());
                    NetworkId netId = event.subject().id();
                    eventExecutor.execute(() -> dependencyStore.remove(netId));
                    break;
                case VTN_PORT_CREATED:
                case VTN_PORT_UPDATED:
                case VTN_PORT_REMOVED:
                default:
                    // do nothing for the other events
                    break;
            }
        }

        private void updateDependency(VtnNetwork subscriber) {
            Set<Dependency> dependencies = subscriber.providers().stream()
                    .map(provider -> Dependency.builder()
                            .subscriber(subscriber)
                            .provider(vtnService.vtnNetwork(provider.id()))
                            .type(provider.type())
                            .build())
                    .collect(Collectors.toSet());
            dependencyStore.put(subscriber.id(), dependencies);
        }
    }

    private class DependencyMapListener implements MapEventListener<NetworkId, Set<Dependency>> {

        @Override
        public void event(MapEvent<NetworkId, Set<Dependency>> event) {
            NodeId leader = leadershipService.getLeader(appId.name());
            if (!Objects.equals(localNodeId, leader)) {
                // do not allow to proceed without leadership
                return;
            }

            switch (event.type()) {
                case UPDATE:
                    log.debug("Subscriber {} updated", event.key());
                    eventExecutor.execute(() -> dependencyUpdated(
                            event.oldValue().value(),
                            event.newValue().value()
                    ));
                    break;
                case INSERT:
                    log.debug("Subscriber {} inserted", event.key());
                    eventExecutor.execute(() -> dependencyUpdated(
                            ImmutableSet.of(),
                            event.newValue().value()
                    ));
                    break;
                case REMOVE:
                    log.debug("Subscriber {} removed", event.key());
                    eventExecutor.execute(() -> dependencyUpdated(
                            event.oldValue().value(),
                            ImmutableSet.of()
                    ));
                    break;
                default:
                    log.error("Unsupported event type");
                    break;
            }
        }

        private void dependencyUpdated(Set<Dependency> oldDeps, Set<Dependency> newDeps) {
            oldDeps.stream().filter(oldDep -> !newDeps.contains(oldDep))
                    .forEach(DependencyManager.this::dependencyRemoved);

            newDeps.stream().filter(newDep -> !oldDeps.contains(newDep))
                    .forEach(DependencyManager.this::dependencyCreated);

            // TODO remove any group if no subscriber exists
        }
    }
}
