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
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.xosclient.api.VtnService;
import org.opencord.cordvtn.impl.AbstractInstanceHandler;
import org.opencord.cordvtn.api.Instance;
import org.opencord.cordvtn.api.InstanceHandler;
import org.opencord.cordvtn.impl.CordVtnPipeline;

import java.util.Optional;

import static org.onosproject.xosclient.api.VtnServiceApi.ServiceType.MANAGEMENT;

/**
 * Provides network connectivity for management network connected instances.
 */
@Component(immediate = true)
public class ManagementInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Activate
    protected void activate() {
        serviceType = Optional.of(MANAGEMENT);
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
        localMgmtNetworkRules(instance, service, true);
    }

    @Override
    public void instanceRemoved(Instance instance) {
        log.info("Instance is removed {}", instance);

        VtnService service = getVtnService(instance.serviceId());
        if (service == null) {
            log.warn("Failed to get VtnService for {}", instance);
            return;
        }

        // TODO check if any stale management network rules are
        localMgmtNetworkRules(instance, service, false);
    }

    private void localMgmtNetworkRules(Instance instance, VtnService service, boolean install) {
        managementPerInstanceRule(instance, install);
        if (install) {
            managementBaseRule(instance, service, true);
        } else if (!hostService.getConnectedHosts(instance.deviceId()).stream()
                .filter(host -> Instance.of(host).serviceId().equals(service.id()))
                .findAny()
                .isPresent()) {
            managementBaseRule(instance, service, false);
        }
    }

    private void managementBaseRule(Instance instance, VtnService service, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP)
                .matchArpTpa(service.serviceIp().getIp4Address())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.LOCAL)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(service.subnet())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .transition(CordVtnPipeline.TABLE_DST_IP)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(service.serviceIp().toIpPrefix())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL)
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }

    private void managementPerInstanceRule(Instance instance, boolean install) {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.LOCAL)
                .matchEthType(Ethernet.TYPE_ARP)
                .matchArpTpa(instance.ipAddress().getIp4Address())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(instance.portNumber())
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_ZERO)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);

        selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(instance.ipAddress().toIpPrefix())
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .setEthDst(instance.mac())
                .setOutput(instance.portNumber())
                .build();

        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_DEFAULT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_DST_IP)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }
}
