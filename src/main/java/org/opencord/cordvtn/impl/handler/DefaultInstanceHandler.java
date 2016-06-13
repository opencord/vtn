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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;

import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.opencord.cordvtn.impl.AbstractInstanceHandler;
import org.opencord.cordvtn.api.CordVtnNode;
import org.opencord.cordvtn.api.Instance;
import org.opencord.cordvtn.api.InstanceHandler;
import org.onosproject.xosclient.api.VtnService;
import org.opencord.cordvtn.impl.CordVtnNodeManager;
import org.opencord.cordvtn.impl.CordVtnPipeline;

import java.util.Optional;

import static org.onosproject.xosclient.api.VtnServiceApi.ServiceType.DEFAULT;

/**
 * Provides network connectivity for default service instances.
 */
@Component(immediate = true)
public class DefaultInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeManager nodeManager;

    @Activate
    protected void activate() {
        serviceType = Optional.of(DEFAULT);
        super.activate();
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void instanceDetected(Instance instance) {
        log.info("Instance is detected {}", instance);

        VtnService service = getVtnService(instance.serviceId());
        if (service == null) {
            log.warn("Failed to get VtnService for {}", instance);
            return;
        }
        defaultConnectionRules(instance, service, true);
    }

    @Override
    public void instanceRemoved(Instance instance) {
        log.info("Instance is removed {}", instance);

        VtnService service = getVtnService(instance.serviceId());
        if (service == null) {
            log.warn("Failed to get VtnService for {}", instance);
            return;
        }
        defaultConnectionRules(instance, service, false);
    }

    private void defaultConnectionRules(Instance instance, VtnService service, boolean install) {
        long vni = service.vni();
        Ip4Prefix serviceIpRange = service.subnet().getIp4Prefix();

        inPortRule(instance, install);
        dstIpRule(instance, vni, install);
        tunnelInRule(instance, vni, install);

        if (install) {
            directAccessRule(serviceIpRange, serviceIpRange, true);
            serviceIsolationRule(serviceIpRange, true);
        } else if (getInstances(service.id()).isEmpty()) {
            directAccessRule(serviceIpRange, serviceIpRange, false);
            serviceIsolationRule(serviceIpRange, false);
        }
    }

    private void inPortRule(Instance instance, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(instance.portNumber())
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(instance.ipAddress().toIpPrefix())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(CordVtnPipeline.TABLE_ACCESS_TYPE)
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

    private void dstIpRule(Instance instance, long vni, boolean install) {
        Ip4Address tunnelIp = nodeManager.dpIp(instance.deviceId()).getIp4Address();

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
                .forTable(CordVtnPipeline.TABLE_DST_IP)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        for (CordVtnNode node : nodeManager.completeNodes()) {
            if (node.intBrId().equals(instance.deviceId())) {
                continue;
            }

            ExtensionTreatment tunnelDst = pipeline.tunnelDstTreatment(node.intBrId(), tunnelIp);
            if (tunnelDst == null) {
                continue;
            }

            treatment = DefaultTrafficTreatment.builder()
                    .setEthDst(instance.mac())
                    .setTunnelId(vni)
                    .extension(tunnelDst, node.intBrId())
                    .setOutput(nodeManager.tunnelPort(node.intBrId()))
                    .build();

            flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                    .forDevice(node.intBrId())
                    .forTable(CordVtnPipeline.TABLE_DST_IP)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRule);
        }
    }

    private void tunnelInRule(Instance instance, long vni, boolean install) {
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

    private void directAccessRule(Ip4Prefix srcRange, Ip4Prefix dstRange, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcRange)
                .matchIPDst(dstRange)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .transition(CordVtnPipeline.TABLE_DST_IP)
                .build();


        nodeManager.completeNodes().stream().forEach(node -> {
            FlowRule flowRuleDirect = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                    .forDevice(node.intBrId())
                    .forTable(CordVtnPipeline.TABLE_ACCESS_TYPE)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRuleDirect);
        });
    }

    private void serviceIsolationRule(Ip4Prefix dstRange, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(dstRange)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .drop()
                .build();

        nodeManager.completeNodes().stream().forEach(node -> {
            FlowRule flowRuleDirect = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(CordVtnPipeline.PRIORITY_LOW)
                    .forDevice(node.intBrId())
                    .forTable(CordVtnPipeline.TABLE_ACCESS_TYPE)
                    .makePermanent()
                    .build();

            pipeline.processFlowRule(install, flowRuleDirect);
        });
    }
}
