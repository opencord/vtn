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
package org.opencord.cordvtn.impl.handler;

import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.opencord.cordvtn.api.core.CordVtnPipeline;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.InstanceHandler;
import org.opencord.cordvtn.api.core.ServiceNetworkService;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.node.CordVtnNodeService;

import static org.opencord.cordvtn.api.core.CordVtnPipeline.PRIORITY_DEFAULT;
import static org.opencord.cordvtn.api.core.CordVtnPipeline.TABLE_ACCESS;
import static org.opencord.cordvtn.api.core.CordVtnPipeline.TABLE_DST;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.FLAT;

/**
 * Provides provider network connectivity to the instance.
 */
@Component(immediate = true)
public class FlatInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceNetworkService snetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeService nodeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Activate
    protected void activate() {
        netTypes = ImmutableSet.of(FLAT);
        super.activate();
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void instanceDetected(Instance instance) {
        log.info("FLAT network instance is detected {}", instance);

        ServiceNetwork snet = snetService.serviceNetwork(instance.netId());
        populateDstIpRule(instance, true);
        populateDstRangeRule(instance.deviceId(),
                getServiceNetwork(instance).subnet(), true);
        populateDirectAccessRule(snet.subnet(), true);
        populateIsolationRule(snet.subnet(), true);
        populateInPortRule(instance, true);
    }

    @Override
    public void instanceRemoved(Instance instance) {
        log.info("FLAT network instance is removed {}", instance);

        populateDstIpRule(instance, false);
        if (getInstances(instance.netId()).isEmpty()) {
            populateDstRangeRule(instance.deviceId(),
                    getServiceNetwork(instance).subnet(), false);
            ServiceNetwork snet = snetService.serviceNetwork(instance.netId());
            populateDirectAccessRule(snet.subnet(), false);
            populateIsolationRule(snet.subnet(), false);
        }
        populateInPortRule(instance, false);
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

    private void populateDirectAccessRule(IpPrefix serviceIpRange, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(serviceIpRange)
                .matchIPDst(serviceIpRange)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(TABLE_DST)
                .build();

        nodeService.completeNodes().forEach(node -> {
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

    private void populateIsolationRule(IpPrefix serviceIpRange, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(serviceIpRange)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .drop()
                .build();

        nodeService.completeNodes().forEach(node -> {
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

    private void populateDstIpRule(Instance instance, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(instance.ipAddress().toIpPrefix())
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
                .forTable(CordVtnPipeline.TABLE_DST)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }

    private void populateDstRangeRule(DeviceId deviceId, IpPrefix dstIpRange, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(dstIpRange)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(dataPort(deviceId))
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_LOW)
                .forDevice(deviceId)
                .forTable(CordVtnPipeline.TABLE_DST)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }
}
