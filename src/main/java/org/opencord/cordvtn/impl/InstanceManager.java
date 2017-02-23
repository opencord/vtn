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
import org.onlab.packet.VlanId;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
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
import org.opencord.cordconfig.CordConfigService;
import org.opencord.cordconfig.access.AccessAgentData;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.InstanceService;
import org.opencord.cordvtn.api.core.ServiceNetworkEvent;
import org.opencord.cordvtn.api.core.ServiceNetworkListener;
import org.opencord.cordvtn.api.core.ServiceNetworkService;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.opencord.cordvtn.api.Constants.CORDVTN_APP_ID;
import static org.opencord.cordvtn.api.Constants.NOT_APPLICABLE;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.ACCESS_AGENT;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Adds or removes instances to network services.
 */
@Component(immediate = true)
@Service
public class InstanceManager extends AbstractProvider implements HostProvider,
        InstanceService {

    protected final Logger log = getLogger(getClass());
    private static final String ERR_SERVICE_NETWORK = "Failed to get service network for %s";
    private static final String ERR_SERVICE_PORT = "Failed to get service port for %s";

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

    // TODO get access agent container information from XOS
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordConfigService cordConfig;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceNetworkService snetService;

    private final ExecutorService eventExecutor =
            newSingleThreadExecutor(groupedThreads(this.getClass().getSimpleName(), "event-handler"));
    private final ServiceNetworkListener snetListener = new InternalServiceNetworkListener();

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
        snetService.addListener(snetListener);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        snetService.removeListener(snetListener);
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

        ServicePort sport = getServicePortByPortName(port.annotations().value(PORT_NAME));
        if (sport == null) {
            log.warn(String.format(ERR_SERVICE_PORT, port));
            return;
        }

        ServiceNetwork snet = snetService.serviceNetwork(sport.networkId());
        if (snet == null) {
            final String error = String.format(ERR_SERVICE_NETWORK, sport);
            throw new IllegalStateException(error);
        }

        // Added CREATE_TIME intentionally to trigger HOST_UPDATED event for the
        // existing instances. Fix this after adding a method to update/reinstall
        // flow rules for the existing service instances.
        DefaultAnnotations.Builder annotations = DefaultAnnotations.builder()
                .set(Instance.NETWORK_TYPE, snet.type().name())
                .set(Instance.NETWORK_ID, snet.id().id())
                .set(Instance.PORT_ID, sport.id().id())
                .set(Instance.CREATE_TIME, String.valueOf(System.currentTimeMillis()));

        HostDescription hostDesc = new DefaultHostDescription(
                sport.mac(),
                VlanId.NONE,
                new HostLocation(connectPoint, System.currentTimeMillis()),
                Sets.newHashSet(sport.ip()),
                annotations.build());

        HostId hostId = HostId.hostId(sport.mac());
        hostProvider.hostDetected(hostId, hostDesc, false);
    }

    @Override
    public void addInstance(HostId hostId, HostDescription description) {
        hostProvider.hostDetected(hostId, description, false);
    }

    @Override
    public void removeInstance(ConnectPoint connectPoint) {
        hostService.getConnectedHosts(connectPoint).forEach(host -> {
            hostProvider.hostVanished(host.id());
        });
    }

    @Override
    public void removeInstance(HostId hostId) {
        hostProvider.hostVanished(hostId);
    }

    private ServicePort getServicePortByPortName(String portName) {
        Optional<ServicePort> sport = snetService.servicePorts()
                .stream()
                .filter(p -> p.id().id().contains(portName.substring(3)))
                .findFirst();
        return sport.isPresent() ? sport.get() : null;
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
        // TODO use instance service instead
        ServicePort sport = snetService.servicePort(portId);
        if (sport == null) {
            final String error = String.format(ERR_SERVICE_PORT, portId);
            throw new IllegalStateException(error);
        }
        Host host = hostService.getHost(HostId.hostId(sport.mac()));
        if (host == null) {
            return null;
        }
        return Instance.of(host);
    }

    private class InternalServiceNetworkListener implements ServiceNetworkListener {

        @Override
        public void event(ServiceNetworkEvent event) {
            NodeId leader = leadershipService.getLeader(appId.name());
            if (!Objects.equals(localNodeId, leader)) {
                // do not allow to proceed without leadership
                return;
            }

            switch (event.type()) {
                case SERVICE_PORT_CREATED:
                case SERVICE_PORT_UPDATED:
                    log.debug("Processing service port {}", event.servicePort());
                    PortId portId = event.servicePort().id();
                    eventExecutor.execute(() -> {
                        Instance instance = getInstance(portId);
                        if (instance != null) {
                            addInstance(instance.host().location());
                        }
                    });
                    break;
                case SERVICE_PORT_REMOVED:
                case SERVICE_NETWORK_CREATED:
                case SERVICE_NETWORK_UPDATED:
                case SERVICE_NETWORK_REMOVED:
                default:
                    // do nothing for the other events
                    break;
            }
        }
    }
}
