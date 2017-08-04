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
package org.opencord.cordvtn.impl.handler;

import com.google.common.collect.ImmutableSet;
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
import org.opencord.cordvtn.api.core.CordVtnPipeline;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.InstanceHandler;
import org.opencord.cordvtn.api.core.ServiceNetworkService;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.node.CordVtnNodeService;

import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.MANAGEMENT_HOST;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.MANAGEMENT_LOCAL;

/**
 * Provides local management network connectivity to the instance. The instance
 * is only accessible via local management network from the host machine.
 */
@Component(immediate = true)
public class ManagementInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceNetworkService snetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeService nodeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnPipeline pipeline;

    @Activate
    protected void activate() {
        netTypes = ImmutableSet.of(MANAGEMENT_LOCAL, MANAGEMENT_HOST);
        super.activate();
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void instanceDetected(Instance instance) {
        ServiceNetwork vtnNet = getServiceNetwork(instance);

        switch (vtnNet.type()) {
            case MANAGEMENT_LOCAL:
                log.info("LOCAL management instance is detected {}", instance);
                populateLocalManagementRules(instance, true);
                break;
            case MANAGEMENT_HOST:
                log.info("HOSTS management instance is detected {}", instance);
                populateHostsManagementRules(instance, true);
                break;
            default:
                break;
        }
    }

    @Override
    public void instanceRemoved(Instance instance) {
        ServiceNetwork vtnNet = getServiceNetwork(instance);

        switch (vtnNet.type()) {
            case MANAGEMENT_LOCAL:
                log.info("LOCAL management instance removed {}", instance);
                populateLocalManagementRules(instance, false);
                break;
            case MANAGEMENT_HOST:
                log.info("HOSTS management instance removed {}", instance);
                populateHostsManagementRules(instance, false);
                break;
            default:
                break;
        }
    }

    private void populateLocalManagementRules(Instance instance, boolean install) {
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
    }

    private void populateHostsManagementRules(Instance instance, boolean install) {
        PortNumber hostMgmtPort = hostManagementPort(instance.deviceId());
        if (hostMgmtPort == null) {
            log.warn("Can not find host management port in {}", instance.deviceId());
            return;
        }

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(instance.portNumber())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(hostMgmtPort)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(CordVtnPipeline.PRIORITY_MANAGEMENT)
                .forDevice(instance.deviceId())
                .forTable(CordVtnPipeline.TABLE_IN_PORT)
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
                .forTable(CordVtnPipeline.TABLE_DST)
                .makePermanent()
                .build();

        pipeline.processFlowRule(install, flowRule);
    }
}
