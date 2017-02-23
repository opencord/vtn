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
package org.opencord.cordvtn.impl.handler;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.host.DefaultHostDescription;
import org.onosproject.net.host.HostDescription;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.InstanceHandler;
import org.opencord.cordvtn.api.core.InstanceService;
import org.opencord.cordvtn.api.net.AddressPair;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.impl.CordVtnNodeManager;
import org.opencord.cordvtn.impl.CordVtnPipeline;

import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.net.flow.criteria.Criterion.Type.IPV4_DST;
import static org.onosproject.net.flow.instructions.L2ModificationInstruction.L2SubType.VLAN_PUSH;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.*;

/**
 * Provides network connectivity for default service instances.
 */
@Component(immediate = true)
public class DefaultInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeManager nodeManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InstanceService instanceService;

    @Activate
    protected void activate() {
        netTypes = ImmutableSet.of(PRIVATE, PUBLIC, VSG);
        super.activate();
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void instanceDetected(Instance instance) {
        if (instance.isAdditionalInstance()) {
            return;
        }
        log.info("Instance is detected or updated {}", instance);

        ServiceNetwork snet = getServiceNetwork(instance);
        populateDefaultRules(instance, snet, true);

        // TODO handle the case that vlan id is added and then removed
        ServicePort sport = getServicePort(instance);
        if (sport.vlanId() != null) {
            populateVlanRule(
                    instance,
                    sport.vlanId(),
                    nodeManager.dataPort(instance.deviceId()),
                    true);
        }
        // FIXME don't add the existing instance again
        sport.addressPairs().forEach(pair -> {
            // add instance for the additional address pairs
            addAdditionalInstance(instance, pair.ip(), pair.mac());
        });
        Set<IpAddress> ipAddrs = sport.addressPairs().stream()
                .map(AddressPair::ip).collect(Collectors.toSet());
        populateAddressPairRule(instance, ipAddrs, true);
    }

    @Override
    public void instanceRemoved(Instance instance) {
        if (instance.isAdditionalInstance()) {
            return;
        }
        log.info("Instance is removed {}", instance);

        ServiceNetwork snet = getServiceNetwork(instance);
        populateDefaultRules(instance, snet, false);

        // FIXME service port might be already removed
        ServicePort sport = getServicePort(instance);
        if (sport.vlanId() != null) {
            populateVlanRule(
                    instance,
                    sport.vlanId(),
                    nodeManager.dataPort(instance.deviceId()),
                    false);
        }
        boolean isOriginalInstance = !instance.isAdditionalInstance();
        Set<IpAddress> ipAddrs = sport.addressPairs().stream()
                .map(AddressPair::ip).collect(Collectors.toSet());
        populateAddressPairRule(
                instance,
                isOriginalInstance ? ImmutableSet.of() : ipAddrs,
                false);
    }

    @Override
    public void instanceUpdated(Instance instance) {
        if (!instance.isAdditionalInstance()) {
            ServicePort sport = getServicePort(instance);
            Set<MacAddress> macAddrs = sport.addressPairs().stream()
                    .map(AddressPair::mac)
                    .collect(Collectors.toSet());
            hostService.getConnectedHosts(instance.host().location()).stream()
                    .filter(h -> !h.mac().equals(instance.mac()))
                    .filter(h -> !macAddrs.contains(h.mac()))
                    .forEach(h -> instanceService.removeInstance(h.id()));
        }
        instanceDetected(instance);
    }

    private void addAdditionalInstance(Instance instance, IpAddress ip, MacAddress mac) {
        HostId hostId = HostId.hostId(mac);
        DefaultAnnotations.Builder annotations = DefaultAnnotations.builder()
                .set(Instance.NETWORK_TYPE, instance.netType().toString())
                .set(Instance.NETWORK_ID, instance.netId().id())
                .set(Instance.PORT_ID, instance.portId().id())
                .set(Instance.ORIGINAL_HOST_ID, instance.host().id().toString())
                .set(Instance.CREATE_TIME, String.valueOf(System.currentTimeMillis()));

        HostDescription hostDesc = new DefaultHostDescription(
                mac,
                VlanId.NONE,
                instance.host().location(),
                Sets.newHashSet(ip),
                annotations.build());

        instanceService.addInstance(hostId, hostDesc);
    }

    private void populateDefaultRules(Instance instance, ServiceNetwork snet, boolean install) {
        long vni = snet.segmentId().id();
        Ip4Prefix serviceIpRange = snet.subnet().getIp4Prefix();

        populateInPortRule(instance, install);
        populateDstIpRule(instance, vni, install);
        populateTunnelInRule(instance, vni, install);

        if (install) {
            populateDirectAccessRule(serviceIpRange, serviceIpRange, true);
            populateServiceIsolationRule(serviceIpRange, true);
        } else if (getInstances(snet.id()).isEmpty()) {
            populateDirectAccessRule(serviceIpRange, serviceIpRange, false);
            populateServiceIsolationRule(serviceIpRange, false);
        }
    }

    private void populateInPortRule(Instance instance, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(instance.portNumber())
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(instance.ipAddress().toIpPrefix())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(CordVtnPipeline.TABLE_ACCESS)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_IN_PORT)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchInPort(instance.portNumber())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .transition(CordVtnPipeline.TABLE_IN_SERVICE)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_LOW)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_IN_PORT)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }

    private void populateDstIpRule(Instance instance, long vni, boolean install) {
        Ip4Address tunnelIp = nodeManager.dataIp(instance.deviceId()).getIp4Address();

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(instance.ipAddress().toIpPrefix())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setEthDst(instance.mac())
                .setOutput(instance.portNumber())
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_DST)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        for (CordVtnNode node : nodeManager.completeNodes()) {
            if (node.integrationBridgeId().equals(instance.deviceId())) {
                continue;
            }

            ExtensionTreatment tunnelDst =
                    pipeline.tunnelDstTreatment(node.integrationBridgeId(), tunnelIp);
            if (tunnelDst == null) {
                continue;
            }

            treatment = DefaultTrafficTreatment.builder()
                    .setEthDst(instance.mac())
                    .setTunnelId(vni)
                    .extension(tunnelDst, node.integrationBridgeId())
                    .setOutput(nodeManager.tunnelPort(node.integrationBridgeId()))
                    .build();

            flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                    .forDevice(node.integrationBridgeId())
                    .forTable(CordVtnPipeline.TABLE_DST)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRule);
        }
    }

    private void populateTunnelInRule(Instance instance, long vni, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchTunnelId(vni)
                .matchEthDst(instance.mac())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(instance.portNumber())
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_TUNNEL_IN)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }

    private void populateDirectAccessRule(Ip4Prefix srcRange, Ip4Prefix dstRange, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcRange)
                .matchIPDst(dstRange)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(CordVtnPipeline.TABLE_DST)
                .build();


        nodeManager.completeNodes().forEach(node -> {
            FlowRule flowRuleDirect = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                    .forDevice(node.integrationBridgeId())
                    .forTable(CordVtnPipeline.TABLE_ACCESS)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRuleDirect);
        });
    }

    private void populateServiceIsolationRule(Ip4Prefix dstRange, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(dstRange)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .drop()
                .build();

        nodeManager.completeNodes().forEach(node -> {
            FlowRule flowRuleDirect = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(CordVtnPipeline.PRIORITY_LOW)
                    .forDevice(node.integrationBridgeId())
                    .forTable(CordVtnPipeline.TABLE_ACCESS)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRuleDirect);
        });
    }

    private void populateVlanRule(Instance instance, VlanId vlanId, PortNumber dataPort,
                                  boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(dataPort)
                .matchVlanId(vlanId)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(instance.portNumber())
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_VLAN)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchInPort(instance.portNumber())
                .matchVlanId(vlanId)
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(dataPort)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_VLAN)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }

    private void populateAddressPairRule(Instance instance, Set<IpAddress> ipAddrs,
                                         boolean install) {
        // for traffic coming from WAN, tag 500 and take through the vSG VM
        // based on destination ip
        ipAddrs.forEach(wanIp -> {
            // for traffic coming from WAN, tag 500 and take through the vSG VM
            TrafficSelector downstream = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(wanIp.toIpPrefix())
                    .build();

            TrafficTreatment downstreamTreatment = DefaultTrafficTreatment.builder()
                    .pushVlan()
                    .setVlanId(CordVtnPipeline.VLAN_WAN)
                    .setEthDst(instance.mac())
                    .setOutput(instance.portNumber())
                    .build();

            FlowRule downstreamFlowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(downstream)
                    .withTreatment(downstreamTreatment)
                    .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                    .forDevice(instance.deviceId())
                    .forTable(CordVtnPipeline.TABLE_DST)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, downstreamFlowRule);
        });

        // remove downstream flow rules for the vSG not shown in vsgWanIps
        for (FlowRule rule : flowRuleService.getFlowRulesById(appId)) {
            if (!rule.deviceId().equals(instance.deviceId())) {
                continue;
            }
            PortNumber output = getOutputFromTreatment(rule);
            if (output == null || !output.equals(instance.portNumber()) ||
                    !isVlanPushFromTreatment(rule)) {
                continue;
            }

            IpPrefix dstIp = getDstIpFromSelector(rule);
            if (dstIp != null && !ipAddrs.contains(dstIp.address())) {
                pipeline.processFlowRule(false, rule);
            }
        }
    }

    private PortNumber getOutputFromTreatment(FlowRule flowRule) {
        Instruction instruction = flowRule.treatment().allInstructions().stream()
                .filter(inst -> inst instanceof Instructions.OutputInstruction)
                .findFirst()
                .orElse(null);
        if (instruction == null) {
            return null;
        }
        return ((Instructions.OutputInstruction) instruction).port();
    }

    private IpPrefix getDstIpFromSelector(FlowRule flowRule) {
        Criterion criterion = flowRule.selector().getCriterion(IPV4_DST);
        if (criterion != null && criterion instanceof IPCriterion) {
            IPCriterion ip = (IPCriterion) criterion;
            return ip.ip();
        } else {
            return null;
        }
    }

    private boolean isVlanPushFromTreatment(FlowRule flowRule) {
        return flowRule.treatment().allInstructions().stream()
                .filter(inst -> inst instanceof L2ModificationInstruction)
                .filter(inst -> ((L2ModificationInstruction) inst).subtype().equals(VLAN_PUSH))
                .findAny()
                .isPresent();
    }
}
