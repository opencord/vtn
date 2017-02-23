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
package org.opencord.cordvtn.impl.handler;

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
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
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
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.ServiceNetworkAdminService;
import org.opencord.cordvtn.api.core.ServiceNetworkEvent;
import org.opencord.cordvtn.api.core.ServiceNetworkListener;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.impl.CordVtnNodeManager;
import org.opencord.cordvtn.impl.CordVtnPipeline;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.net.group.DefaultGroupBucket.createSelectGroupBucket;
import static org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType.BIDIRECTIONAL;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.*;
import static org.opencord.cordvtn.impl.CordVtnPipeline.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provisions service dependencies between service networks.
 */
@Component(immediate = true)
@Service
public class DependencyHandler extends AbstractInstanceHandler {

    protected final Logger log = getLogger(getClass());

    private static final String ERR_NET_FAIL = "Failed to get VTN network ";
    private static final String ADDED = "Added ";
    private static final String REMOVED = "Removed ";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeManager nodeManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceNetworkAdminService snetService;

    private final ServiceNetworkListener snetListener = new InternalServiceNetworkListener();
    private NodeId localNodeId;

    @Activate
    protected void activate() {
        netTypes = ImmutableSet.of(PRIVATE, PUBLIC, VSG);
        super.activate();
        localNodeId = clusterService.getLocalNode().id();
        leadershipService.runForLeadership(appId.name());
        snetService.addListener(snetListener);
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
        snetService.removeListener(snetListener);
        leadershipService.withdraw(appId.name());
    }

    @Override
    public void instanceDetected(Instance instance) {
        ServiceNetwork snet = snetService.serviceNetwork(instance.netId());
        if (snet == null) {
            final String error = ERR_NET_FAIL + instance.netId();
            throw new IllegalStateException(error);
        }
        if (!snet.providers().isEmpty()) {
            updateSubscriberInstances(snet, instance, true);
        }
        // TODO check if subscribers on this network
        updateProviderInstances(snet);
    }

    @Override
    public void instanceRemoved(Instance instance) {
        ServiceNetwork snet = snetService.serviceNetwork(instance.netId());
        if (snet == null) {
            final String error = ERR_NET_FAIL + instance.netId();
            throw new IllegalStateException(error);
        }
        if (!snet.providers().isEmpty()) {
            updateSubscriberInstances(snet, instance, false);
        }
        // TODO check if subscribers on this network and remove group if unused
        updateProviderInstances(snet);
    }

    private void dependencyAdded(ServiceNetwork subscriber, ServiceNetwork provider,
                                 DependencyType type) {
        populateDependencyRules(subscriber, provider, type, true);
        log.info("Dependency is created subscriber:{}, provider:{}, type: {}",
                 subscriber.name(),
                 provider.name(), type.name());
    }

    private void dependencyRemoved(ServiceNetwork subscriber, ServiceNetwork provider,
                                   DependencyType type) {
        populateDependencyRules(subscriber, provider, type, false);
        if (!isProviderInUse(provider.id())) {
            removeGroup(provider.id());
        }
        log.info("Dependency is removed subscriber:{}, provider:{}, type: {}",
                 subscriber.name(),
                 provider.name(), type.name());
    }

    private void updateProviderInstances(ServiceNetwork provider) {
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

    private void updateSubscriberInstances(ServiceNetwork subscriber, Instance instance,
                                           boolean isDetected) {
        DeviceId deviceId = instance.deviceId();
        final String isAdded = isDetected ? ADDED : REMOVED;
        subscriber.providers().keySet().forEach(providerId -> {
            populateInPortRule(
                    ImmutableMap.of(deviceId, ImmutableSet.of(instance.portNumber())),
                    ImmutableMap.of(deviceId, getGroupId(providerId, deviceId)),
                    isDetected);
            log.info(isAdded + "subscriber instance({}) for provider({})",
                     instance.host().id(), providerId.id());
        });
    }

    private boolean isProviderInUse(NetworkId providerId) {
        return snetService.serviceNetworks().stream()
                .flatMap(net -> net.providers().keySet().stream())
                .anyMatch(provider -> Objects.equals(provider, providerId));
    }

    private void removeGroup(NetworkId netId) {
        GroupKey groupKey = getGroupKey(netId);
        nodeManager.completeNodes().forEach(node -> {
            DeviceId deviceId = node.integrationBridgeId();
            Group group = groupService.getGroup(deviceId, groupKey);
            if (group != null) {
                groupService.removeGroup(deviceId, groupKey, appId);
            }
        });
        log.debug("Removed group for network {}", netId);
    }

    private GroupId getGroupId(NetworkId netId, DeviceId deviceId) {
        return new GroupId(Objects.hash(netId, deviceId));
    }

    private GroupKey getGroupKey(NetworkId netId) {
        return new DefaultGroupKey(netId.id().getBytes());
    }

    private GroupId getProviderGroup(ServiceNetwork provider, DeviceId deviceId) {
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

    private void populateDependencyRules(ServiceNetwork subscriber, ServiceNetwork provider,
                                         DependencyType type, boolean install) {
        Map<DeviceId, GroupId> providerGroups = Maps.newHashMap();
        Map<DeviceId, Set<PortNumber>> subscriberPorts = Maps.newHashMap();

        nodeManager.completeNodes().forEach(node -> {
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

        // TODO support IPv6
        IpPrefix sSubnet = subscriber.subnet().getIp4Prefix();
        IpPrefix pSubnet = provider.subnet().getIp4Prefix();

        populateInPortRule(subscriberPorts, providerGroups, install);
        populateIndirectAccessRule(
                sSubnet,
                provider.serviceIp().getIp4Address(),
                providerGroups,
                install);
        populateDirectAccessRule(sSubnet, pSubnet, install);
        if (type == BIDIRECTIONAL) {
            populateDirectAccessRule(pSubnet, sSubnet, install);
        }
    }

    private void populateIndirectAccessRule(IpPrefix srcSubnet, IpAddress serviceIp,
                                            Map<DeviceId, GroupId> outGroups,
                                            boolean install) {
        // TODO support IPv6
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcSubnet)
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

    private void populateDirectAccessRule(IpPrefix srcIp, IpPrefix dstIp, boolean install) {
        // TODO support IPv6
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcIp)
                .matchIPDst(dstIp)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(TABLE_DST)
                .build();

        nodeManager.completeNodes().forEach(node -> {
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
            ports.forEach(port -> {
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

    private GroupBuckets getProviderGroupBuckets(DeviceId deviceId, long tunnelId,
                                                 Set<Instance> instances) {
        List<GroupBucket> buckets = Lists.newArrayList();
        instances.forEach(instance -> {
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

    private class InternalServiceNetworkListener implements ServiceNetworkListener {

        @Override
        public boolean isRelevant(ServiceNetworkEvent event) {
            // do not allow to proceed without leadership
            NodeId leader = leadershipService.getLeader(appId.name());
            return Objects.equals(localNodeId, leader);
        }

        @Override
        public void event(ServiceNetworkEvent event) {

            switch (event.type()) {
                case SERVICE_NETWORK_PROVIDER_ADDED:
                    log.debug("Dependency added: {}", event);
                    eventExecutor.execute(() -> {
                        dependencyAdded(
                                event.subject(),
                                event.provider().provider(),
                                event.provider().type());
                    });
                    break;
                case SERVICE_NETWORK_PROVIDER_REMOVED:
                    log.debug("Dependency removed: {}", event);
                    eventExecutor.execute(() -> {
                        dependencyRemoved(
                                event.subject(),
                                event.provider().provider(),
                                event.provider().type());
                    });
                    break;
                case SERVICE_NETWORK_CREATED:
                case SERVICE_NETWORK_UPDATED:
                    // TODO handle dependency rule related element updates
                case SERVICE_NETWORK_REMOVED:
                case SERVICE_PORT_CREATED:
                case SERVICE_PORT_UPDATED:
                case SERVICE_PORT_REMOVED:
                default:
                    // do nothing for the other events
                    break;
            }
        }
    }
}
