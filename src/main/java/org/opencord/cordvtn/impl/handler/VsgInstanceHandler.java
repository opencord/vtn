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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Host;
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
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.host.DefaultHostDescription;
import org.onosproject.net.host.HostDescription;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.InstanceHandler;
import org.opencord.cordvtn.api.core.InstanceService;
import org.opencord.cordvtn.api.net.AddressPair;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.impl.CordVtnNodeManager;
import org.opencord.cordvtn.impl.CordVtnPipeline;

import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.net.flow.criteria.Criterion.Type.IPV4_DST;
import static org.onosproject.net.flow.instructions.L2ModificationInstruction.L2SubType.VLAN_PUSH;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.VSG;

/**
 * Provides network connectivity for vSG instances.
 */
@Component(immediate = true)
public final class VsgInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    private static final String STAG = "stag";
    private static final String VSG_VM = "vsgVm";
    private static final String ERR_VSG_VM = "vSG VM does not exist for %s";
    private static final String MSG_VSG_VM = "vSG VM %s: %s";
    private static final String MSG_VSG_CONTAINER = "vSG container %s: %s";
    private static final String MSG_DETECTED = "detected";
    private static final String MSG_REMOVED = "removed";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeManager nodeManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InstanceService instanceService;

    @Activate
    protected void activate() {
        netTypes = ImmutableSet.of(VSG);
        super.activate();
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void instanceDetected(Instance instance) {
        if (isVsgContainer(instance)) {
            log.info(String.format(MSG_VSG_CONTAINER, MSG_DETECTED, instance));
            Instance vsgVm = getVsgVm(instance);
            if (vsgVm == null) {
                final String error = String.format(ERR_VSG_VM, instance);
                throw new IllegalStateException(error);
            }

            ServicePort sport = getServicePort(vsgVm);
            Set<IpAddress> wanIps = sport.addressPairs().stream()
                    .map(AddressPair::ip).collect(Collectors.toSet());
            populateVsgRules(
                    vsgVm, sport.vlanId(),
                    nodeManager.dataPort(vsgVm.deviceId()),
                    wanIps, true);
        } else {
            log.info(String.format(MSG_VSG_VM, MSG_DETECTED, instance));
            ServicePort sport = snetService.servicePort(instance.portId());
            if (sport == null || sport.vlanId() == null) {
                // service port can be updated after instance is created
                return;
            }

            // insert vSG containers inside the vSG VM as a host
            sport.addressPairs().stream().forEach(pair -> addVsgContainer(
                    instance,
                    pair.ip(),
                    pair.mac(),
                    sport.vlanId()));
        }
    }

    @Override
    public void instanceUpdated(Instance instance) {
        if (!isVsgContainer(instance)) {
            Set<MacAddress> vsgMacs = getServicePort(instance).addressPairs().stream()
                    .map(AddressPair::mac)
                    .collect(Collectors.toSet());
            hostService.getConnectedHosts(instance.host().location()).stream()
                    .filter(h -> !h.mac().equals(instance.mac()))
                    .filter(h -> !vsgMacs.contains(h.mac()))
                    .forEach(h -> instanceService.removeNestedInstance(h.id()));
        }
        instanceDetected(instance);
    }

    @Override
    public void instanceRemoved(Instance instance) {
        boolean isVsgContainer = isVsgContainer(instance);
        log.info(String.format(
                isVsgContainer ? MSG_VSG_CONTAINER : MSG_VSG_VM,
                MSG_REMOVED, instance));

        Instance vsgVm = isVsgContainer ? getVsgVm(instance) : instance;
        if (vsgVm == null) {
            // the rules are removed when VM is removed, do nothing
            return;
        }

        // FIXME service port can be removed already
        ServicePort sport = getServicePort(instance);
        Set<IpAddress> wanIps = sport.addressPairs().stream()
                .map(AddressPair::ip).collect(Collectors.toSet());
        populateVsgRules(
                vsgVm, sport.vlanId(),
                nodeManager.dataPort(vsgVm.deviceId()),
                isVsgContainer ? wanIps : ImmutableSet.of(),
                false);
    }

    @Override
    protected ServicePort getServicePort(Instance instance) {
        ServicePort sport = snetService.servicePort(instance.portId());
        if (sport == null || sport.vlanId() == null) {
            final String error = String.format(ERR_VTN_PORT, instance);
            throw new IllegalStateException(error);
        }
        return sport;
    }

    private boolean isVsgContainer(Instance instance) {
        return  !Strings.isNullOrEmpty(instance.getAnnotation(STAG)) &&
                !Strings.isNullOrEmpty(instance.getAnnotation(VSG_VM));
    }

    private Instance getVsgVm(Instance vsgContainer) {
        String vsgVmId = vsgContainer.getAnnotation(VSG_VM);
        Host host = hostService.getHost(HostId.hostId(vsgVmId));
        if (host == null) {
            return null;
        }
        return Instance.of(host);
    }

    private void addVsgContainer(Instance vsgVm, IpAddress vsgWanIp, MacAddress vsgMac,
                                 VlanId stag) {
        HostId hostId = HostId.hostId(vsgMac);
        DefaultAnnotations.Builder annotations = DefaultAnnotations.builder()
                .set(Instance.NETWORK_TYPE, vsgVm.netType().toString())
                .set(Instance.NETWORK_ID, vsgVm.netId().id())
                .set(Instance.PORT_ID, vsgVm.portId().id())
                .set(STAG, stag.toString())
                .set(VSG_VM, vsgVm.host().id().toString())
                .set(Instance.CREATE_TIME, String.valueOf(System.currentTimeMillis()));

        HostDescription hostDesc = new DefaultHostDescription(
                vsgMac,
                VlanId.NONE,
                vsgVm.host().location(),
                Sets.newHashSet(vsgWanIp),
                annotations.build());

        instanceService.addNestedInstance(hostId, hostDesc);
    }

    private void populateVsgRules(Instance vsgVm, VlanId stag, PortNumber dataPort,
                                  Set<IpAddress> vsgWanIps, boolean install) {
        // for traffics with s-tag, strip the tag and take through the vSG VM
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(dataPort)
                .matchVlanId(stag)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(vsgVm.portNumber())
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(vsgVm.deviceId())
                .forTable(CordVtnPipeline.TABLE_VLAN)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        // for traffics with customer vlan, tag with the service vlan based on input port with
        // lower priority to avoid conflict with WAN tag
        selector = DefaultTrafficSelector.builder()
                .matchInPort(vsgVm.portNumber())
                .matchVlanId(stag)
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(dataPort)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(vsgVm.deviceId())
                .forTable(CordVtnPipeline.TABLE_VLAN)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        // for traffic coming from WAN, tag 500 and take through the vSG VM
        // based on destination ip
        vsgWanIps.stream().forEach(wanIp -> {
            // for traffic coming from WAN, tag 500 and take through the vSG VM
            TrafficSelector downstream = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(wanIp.toIpPrefix())
                    .build();

            TrafficTreatment downstreamTreatment = DefaultTrafficTreatment.builder()
                    .pushVlan()
                    .setVlanId(CordVtnPipeline.VLAN_WAN)
                    .setEthDst(vsgVm.mac())
                    .setOutput(vsgVm.portNumber())
                    .build();

            FlowRule downstreamFlowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(downstream)
                    .withTreatment(downstreamTreatment)
                    .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                    .forDevice(vsgVm.deviceId())
                    .forTable(CordVtnPipeline.TABLE_DST)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, downstreamFlowRule);
        });

        // remove downstream flow rules for the vSG not shown in vsgWanIps
        for (FlowRule rule : flowRuleService.getFlowRulesById(appId)) {
            if (!rule.deviceId().equals(vsgVm.deviceId())) {
                continue;
            }
            PortNumber output = getOutputFromTreatment(rule);
            if (output == null || !output.equals(vsgVm.portNumber()) ||
                    !isVlanPushFromTreatment(rule)) {
                continue;
            }

            IpPrefix dstIp = getDstIpFromSelector(rule);
            if (dstIp != null && !vsgWanIps.contains(dstIp.address())) {
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
