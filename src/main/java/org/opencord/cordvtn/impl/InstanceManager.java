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

import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.opencord.cordconfig.CordConfigService;
import org.opencord.cordconfig.access.AccessAgentData;
import org.opencord.cordvtn.api.CordVtnService;
import org.opencord.cordvtn.api.Instance;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.dhcp.DhcpService;
import org.onosproject.dhcp.IpAssignment;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.DefaultHostDescription;
import org.onosproject.net.host.HostDescription;
import org.onosproject.net.host.HostProvider;
import org.onosproject.net.host.HostProviderRegistry;
import org.onosproject.net.host.HostProviderService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.opencord.cordvtn.api.InstanceService;
import org.opencord.cordvtn.api.PortId;
import org.opencord.cordvtn.api.VtnNetwork;
import org.opencord.cordvtn.api.VtnNetworkEvent;
import org.opencord.cordvtn.api.VtnNetworkListener;
import org.opencord.cordvtn.api.VtnPort;
import org.slf4j.Logger;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.dhcp.IpAssignment.AssignmentStatus.Option_RangeNotEnforced;
import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.opencord.cordvtn.api.Constants.*;
import static org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType.ACCESS_AGENT;
import static org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType.MANAGEMENT_HOST;
import static org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType.MANAGEMENT_LOCAL;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Adds or removes instances to network services.
 */
@Component(immediate = true)
@Service
public class InstanceManager extends AbstractProvider implements HostProvider,
        InstanceService {

    protected final Logger log = getLogger(getClass());
    private static final String ERR_VTN_NETWORK = "Faild to get VTN network for %s";
    private static final String ERR_VTN_PORT = "Faild to get VTN port for %s";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostProviderRegistry hostProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DhcpService dhcpService;

    // TODO get access agent container information from XOS
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordConfigService cordConfig;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnService vtnService;

    private final ExecutorService eventExecutor =
            newSingleThreadExecutor(groupedThreads(this.getClass().getSimpleName(), "event-handler"));
    private final VtnNetworkListener vtnNetListener = new InternalVtnNetworkListener();

    private ApplicationId appId;
    private NodeId localNodeId;
    private HostProviderService hostProvider;

    /**
     * Creates an cordvtn host location provider.
     */
    public InstanceManager() {
        super(new ProviderId("host", CORDVTN_APP_ID));
    }

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(CORDVTN_APP_ID);
        localNodeId = clusterService.getLocalNode().id();
        leadershipService.runForLeadership(appId.name());

        hostProvider = hostProviderRegistry.register(this);
        vtnService.addListener(vtnNetListener);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        vtnService.removeListener(vtnNetListener);
        hostProviderRegistry.unregister(this);
        eventExecutor.shutdown();
        leadershipService.withdraw(appId.name());

        log.info("Stopped");
    }

    @Override
    public void triggerProbe(Host host) {
        /*
         * Note: In CORD deployment, we assume that all hosts are configured.
         * Therefore no probe is required.
         */
    }

    @Override
    public void addInstance(ConnectPoint connectPoint) {
        Port port = deviceService.getPort(connectPoint.deviceId(), connectPoint.port());
        if (port == null) {
            log.debug("No port found from {}", connectPoint);
            return;
        }

        // TODO remove this when XOS provides access agent information
        // and handle it the same way wit the other instances
        if (isAccessAgent(connectPoint)) {
            addAccessAgentInstance(connectPoint);
            return;
        }

        VtnPort vtnPort = vtnService.vtnPort(port.annotations().value(PORT_NAME));
        if (vtnPort == null) {
            log.warn(String.format(ERR_VTN_PORT, port));
            return;
        }

        VtnNetwork vtnNet = vtnService.vtnNetwork(vtnPort.netId());
        if (vtnNet == null) {
            log.warn(String.format(ERR_VTN_NETWORK, vtnPort));
            return;
        }

        // register DHCP lease for the new instance
        registerDhcpLease(vtnPort.mac(), vtnPort.ip().getIp4Address(), vtnNet);

        // Added CREATE_TIME intentionally to trigger HOST_UPDATED event for the
        // existing instances.
        DefaultAnnotations.Builder annotations = DefaultAnnotations.builder()
                .set(Instance.NETWORK_TYPE, vtnNet.type().name())
                .set(Instance.NETWORK_ID, vtnNet.id().id())
                .set(Instance.PORT_ID, vtnPort.id().id())
                .set(Instance.CREATE_TIME, String.valueOf(System.currentTimeMillis()));

        HostDescription hostDesc = new DefaultHostDescription(
                vtnPort.mac(),
                VlanId.NONE,
                new HostLocation(connectPoint, System.currentTimeMillis()),
                Sets.newHashSet(vtnPort.ip()),
                annotations.build());

        HostId hostId = HostId.hostId(vtnPort.mac());
        hostProvider.hostDetected(hostId, hostDesc, false);
    }

    @Override
    public void addNestedInstance(HostId hostId, HostDescription description) {
        DefaultAnnotations annotations  = DefaultAnnotations.builder()
                .set(Instance.NESTED_INSTANCE, Instance.TRUE)
                .build();
        annotations = annotations.merge(annotations, description.annotations());

        HostDescription nestedHost = new DefaultHostDescription(
                description.hwAddress(),
                description.vlan(),
                description.location(),
                description.ipAddress(),
                annotations);

        hostProvider.hostDetected(hostId, nestedHost, false);
    }

    @Override
    public void removeInstance(ConnectPoint connectPoint) {
        hostService.getConnectedHosts(connectPoint).stream()
                .forEach(host -> {
                    dhcpService.removeStaticMapping(host.mac());
                    hostProvider.hostVanished(host.id());
                });
    }

    @Override
    public void removeNestedInstance(HostId hostId) {
        hostProvider.hostVanished(hostId);
    }

    private void registerDhcpLease(MacAddress macAddr, Ip4Address ipAddr, VtnNetwork vtnNet) {
        Ip4Address broadcast = Ip4Address.makeMaskedAddress(
                ipAddr,
                vtnNet.subnet().prefixLength());

        IpAssignment.Builder ipBuilder = IpAssignment.builder()
                .ipAddress(ipAddr)
                .leasePeriod(DHCP_INFINITE_LEASE)
                .timestamp(new Date())
                .subnetMask(Ip4Address.makeMaskPrefix(vtnNet.subnet().prefixLength()))
                .broadcast(broadcast)
                .domainServer(DEFAULT_DNS)
                .assignmentStatus(Option_RangeNotEnforced);

        if (vtnNet.type() != MANAGEMENT_HOST && vtnNet.type() != MANAGEMENT_LOCAL) {
            ipBuilder = ipBuilder.routerAddress(vtnNet.serviceIp().getIp4Address());
        }

        log.debug("Set static DHCP mapping for {} {}", macAddr, ipAddr);
        dhcpService.setStaticMapping(macAddr, ipBuilder.build());
    }

    // TODO remove this when XOS provides access agent information
    private boolean isAccessAgent(ConnectPoint connectPoint) {
        Optional<AccessAgentData> agent = cordConfig.getAccessAgent(connectPoint.deviceId());
        if (!agent.isPresent() || !agent.get().getVtnLocation().isPresent()) {
            return false;
        }
        return agent.get().getVtnLocation().get().port().equals(connectPoint.port());
    }

    // TODO remove this when XOS provides access agent information
    private void addAccessAgentInstance(ConnectPoint connectPoint) {
        AccessAgentData agent = cordConfig.getAccessAgent(connectPoint.deviceId()).get();
        DefaultAnnotations.Builder annotations = DefaultAnnotations.builder()
                .set(Instance.NETWORK_TYPE, ACCESS_AGENT.name())
                .set(Instance.NETWORK_ID, NOT_APPLICABLE)
                .set(Instance.PORT_ID, NOT_APPLICABLE)
                .set(Instance.CREATE_TIME, String.valueOf(System.currentTimeMillis()));

        HostDescription hostDesc = new DefaultHostDescription(
                agent.getAgentMac(),
                VlanId.NONE,
                new HostLocation(connectPoint, System.currentTimeMillis()),
                Sets.newHashSet(),
                annotations.build());

        HostId hostId = HostId.hostId(agent.getAgentMac());
        hostProvider.hostDetected(hostId, hostDesc, false);
    }

    private Instance getInstance(PortId portId) {
        VtnPort vtnPort = vtnService.vtnPort(portId);
        if (vtnPort == null) {
            final String error = "Failed to build VTN port for " + portId.id();
            throw new IllegalStateException(error);
        }
        Host host = hostService.getHost(HostId.hostId(vtnPort.mac()));
        if (host == null) {
            return null;
        }
        return Instance.of(host);
    }

    private class InternalVtnNetworkListener implements VtnNetworkListener {

        @Override
        public void event(VtnNetworkEvent event) {
            NodeId leader = leadershipService.getLeader(appId.name());
            if (!Objects.equals(localNodeId, leader)) {
                // do not allow to proceed without leadership
                return;
            }

            switch (event.type()) {
                case VTN_PORT_CREATED:
                case VTN_PORT_UPDATED:
                    log.debug("Processing service port {}", event.vtnPort());
                    PortId portId = event.vtnPort().id();
                    eventExecutor.execute(() -> {
                        Instance instance = getInstance(portId);
                        if (instance != null) {
                            addInstance(instance.host().location());
                        }
                    });
                    break;
                case VTN_PORT_REMOVED:
                case VTN_NETWORK_CREATED:
                case VTN_NETWORK_UPDATED:
                case VTN_NETWORK_REMOVED:
                default:
                    // do nothing for the other events
                    break;
            }
        }
    }
}
