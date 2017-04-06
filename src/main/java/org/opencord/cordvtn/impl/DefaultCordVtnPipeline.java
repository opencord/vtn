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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.VlanId;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Port;
import org.opencord.cordvtn.api.Constants;
import org.opencord.cordvtn.api.core.CordVtnPipeline;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleOperationsContext;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.slf4j.Logger;


import static com.google.common.base.Preconditions.checkArgument;
import static org.opencord.cordvtn.api.Constants.DEFAULT_TUNNEL;
import static org.opencord.cordvtn.api.node.CordVtnNodeState.COMPLETE;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of cordvtn pipeline.
 */
@Component(immediate = true)
@Service
public class DefaultCordVtnPipeline implements CordVtnPipeline {

    protected final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    private static final int VXLAN_UDP_PORT = 4789;

    private ApplicationId appId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(Constants.CORDVTN_APP_ID);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void cleanupPipeline() {
        flowRuleService.getFlowRulesById(appId).forEach(flowRule -> processFlowRule(false, flowRule));
    }

    @Override
    public void initPipeline(CordVtnNode node) {
        checkArgument(node.state() == COMPLETE, "Node is not in COMPLETE state");

        PortNumber dataPort = getPortNumber(node.integrationBridgeId(), node.dataInterface());
        PortNumber tunnelPort = getPortNumber(node.integrationBridgeId(), DEFAULT_TUNNEL);
        PortNumber hostMgmtPort = node.hostManagementInterface() == null ?
                null : getPortNumber(node.integrationBridgeId(), node.hostManagementInterface());

        processTableZero(node.integrationBridgeId(),
                dataPort,
                node.dataIp().ip(),
                node.localManagementIp().ip());

        processInPortTable(node.integrationBridgeId(),
                tunnelPort,
                dataPort,
                hostMgmtPort);

        processAccessTypeTable(node.integrationBridgeId(), dataPort);
        processVlanTable(node.integrationBridgeId(), dataPort);
    }

    @Override
    public void processFlowRule(boolean install, FlowRule rule) {
        FlowRuleOperations.Builder oBuilder = FlowRuleOperations.builder();
        oBuilder = install ? oBuilder.add(rule) : oBuilder.remove(rule);

        flowRuleService.apply(oBuilder.build(new FlowRuleOperationsContext() {
            @Override
            public void onError(FlowRuleOperations ops) {
                log.error(String.format("Failed %s, %s", ops.toString(), rule.toString()));
            }
        }));
    }

    private void processTableZero(DeviceId deviceId, PortNumber dataPort, IpAddress dataIp,
                                  IpAddress localMgmtIp) {
        vxlanShuttleRule(deviceId, dataPort, dataIp);
        localManagementBaseRule(deviceId, localMgmtIp.getIp4Address());

        // take all vlan tagged packet to the VLAN table
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchVlanId(VlanId.ANY)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(TABLE_VLAN)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_MANAGEMENT)
                .forDevice(deviceId)
                .forTable(TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        // take all other packets to the next table
        selector = DefaultTrafficSelector.builder()
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .transition(TABLE_IN_PORT)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_ZERO)
                .forDevice(deviceId)
                .forTable(TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);
    }

    private void vxlanShuttleRule(DeviceId deviceId, PortNumber dataPort, IpAddress dataIp) {
        // take vxlan packet out onto the physical port
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.LOCAL)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(dataPort)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_HIGH)
                .forDevice(deviceId)
                .forTable(TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        // take a vxlan encap'd packet through the Linux stack
        selector = DefaultTrafficSelector.builder()
                .matchInPort(dataPort)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpDst(TpPort.tpPort(VXLAN_UDP_PORT))
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_HIGH)
                .forDevice(deviceId)
                .forTable(TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        // take a packet to the data plane ip through Linux stack
        selector = DefaultTrafficSelector.builder()
                .matchInPort(dataPort)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(dataIp.toIpPrefix())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_HIGH)
                .forDevice(deviceId)
                .forTable(TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        // take an arp packet from physical through Linux stack
        selector = DefaultTrafficSelector.builder()
                .matchInPort(dataPort)
                .matchEthType(Ethernet.TYPE_ARP)
                .matchArpTpa(dataIp.getIp4Address())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_HIGH)
                .forDevice(deviceId)
                .forTable(TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);
    }

    private void localManagementBaseRule(DeviceId deviceId, Ip4Address localMgmtIp) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP)
                .matchArpTpa(localMgmtIp)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(deviceId)
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.LOCAL)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(localMgmtIp.toIpPrefix())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .transition(CordVtnPipeline.TABLE_DST)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(deviceId)
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(localMgmtIp.toIpPrefix())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(deviceId)
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.LOCAL)
                .matchEthType(Ethernet.TYPE_ARP)
                .matchArpSpa(localMgmtIp)
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.CONTROLLER)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(deviceId)
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);
    }

    private void processInPortTable(DeviceId deviceId, PortNumber tunnelPort, PortNumber dataPort,
                                    PortNumber hostMgmtPort) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(tunnelPort)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(TABLE_TUNNEL_IN)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_DEFAULT)
                .forDevice(deviceId)
                .forTable(TABLE_IN_PORT)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchInPort(dataPort)
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .transition(TABLE_DST)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_DEFAULT)
                .forDevice(deviceId)
                .forTable(TABLE_IN_PORT)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        if (hostMgmtPort != null) {
            selector = DefaultTrafficSelector.builder()
                    .matchInPort(hostMgmtPort)
                    .build();

            treatment = DefaultTrafficTreatment.builder()
                    .transition(TABLE_DST)
                    .build();

            flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(PRIORITY_DEFAULT)
                    .forDevice(deviceId)
                    .forTable(TABLE_IN_PORT)
                    .makePermanent()
                    .build();

            processFlowRule(true, flowRule);
        }
    }

    private void processAccessTypeTable(DeviceId deviceId, PortNumber dataPort) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(dataPort)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_ZERO)
                .forDevice(deviceId)
                .forTable(TABLE_ACCESS)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);
    }

    private void processVlanTable(DeviceId deviceId, PortNumber dataPort) {
        // for traffic going out to WAN, strip vid 500 and take through data plane interface
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchVlanId(VLAN_WAN)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .popVlan()
                .setOutput(dataPort)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_DEFAULT)
                .forDevice(deviceId)
                .forTable(TABLE_VLAN)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchVlanId(VLAN_WAN)
                .matchEthType(Ethernet.TYPE_ARP)
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.CONTROLLER)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(PRIORITY_HIGH)
                .forDevice(deviceId)
                .forTable(TABLE_VLAN)
                .makePermanent()
                .build();

        processFlowRule(true, flowRule);
    }

    private PortNumber getPortNumber(DeviceId deviceId, String portName) {
        return deviceService.getPorts(deviceId).stream()
                .filter(p -> p.annotations().value(AnnotationKeys.PORT_NAME).equals(portName) &&
                        p.isEnabled())
                .map(Port::number)
                .findAny().orElse(null);
    }
}
