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
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.util.Tools;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.ExtensionTreatmentResolver;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.instructions.ExtensionPropertyException;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.opencord.cordvtn.api.Constants;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.InstanceHandler;
import org.opencord.cordvtn.api.core.ServiceNetworkService;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeService;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.onosproject.net.flow.instructions.ExtensionTreatmentType.ExtensionTreatmentTypes.NICIRA_SET_TUNNEL_DST;
import static org.opencord.cordvtn.api.Constants.DEFAULT_TUNNEL;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides default virtual network connectivity for service instances.
 */
public abstract class AbstractInstanceHandler implements InstanceHandler {

    protected final Logger log = getLogger(getClass());

    protected static final String ERR_VTN_NETWORK = "Failed to get VTN network for %s";
    protected static final String ERR_VTN_PORT = "Failed to get VTN port for %s";
    protected static final String PROPERTY_TUNNEL_DST = "tunnelDst";

    protected CoreService coreService;
    protected MastershipService mastershipService;
    protected HostService hostService;
    protected DeviceService deviceService;
    protected ServiceNetworkService snetService;
    protected CordVtnNodeService nodeService;
    protected ApplicationId appId;
    protected Set<ServiceNetwork.NetworkType> netTypes = ImmutableSet.of();

    protected HostListener hostListener = new InternalHostListener();

    protected final ExecutorService eventExecutor = newSingleThreadExecutor(
            groupedThreads(this.getClass().getSimpleName(), "event-handler", log));

    protected void activate() {
        ServiceDirectory services = new DefaultServiceDirectory();
        coreService = services.get(CoreService.class);
        mastershipService = services.get(MastershipService.class);
        hostService = services.get(HostService.class);
        deviceService = services.get(DeviceService.class);
        snetService = services.get(ServiceNetworkService.class);
        nodeService = services.get(CordVtnNodeService.class);

        appId = coreService.registerApplication(Constants.CORDVTN_APP_ID);
        hostService.addListener(hostListener);

        log.info("Started");
    }

    protected void deactivate() {
        hostService.removeListener(hostListener);
        eventExecutor.shutdown();

        log.info("Stopped");
    }

    @Override
    public void instanceUpdated(Instance instance) {
        instanceDetected(instance);
    }

    protected Set<Instance> getInstances(NetworkId netId) {
        return Tools.stream(hostService.getHosts())
                .filter(host -> Objects.equals(
                        netId.id(),
                        host.annotations().value(Instance.NETWORK_ID)))
                .map(Instance::of)
                .collect(Collectors.toSet());
    }

    protected ServiceNetwork getServiceNetwork(Instance instance) {
        ServiceNetwork snet = snetService.serviceNetwork(instance.netId());
        if (snet == null) {
            final String error = String.format(ERR_VTN_NETWORK, instance);
            throw new IllegalStateException(error);
        }
        return snet;
    }

    protected ServicePort getServicePort(Instance instance) {
        ServicePort sport = snetService.servicePort(instance.portId());
        if (sport == null) {
            final String error = String.format(ERR_VTN_PORT, instance);
            throw new IllegalStateException(error);
        }
        return sport;
    }

    protected PortNumber dataPort(DeviceId deviceId) {
        CordVtnNode node = nodeService.node(deviceId);
        if (node == null) {
            log.debug("Failed to get node for {}", deviceId);
            return null;
        }
        Optional<PortNumber> port = getPortNumber(deviceId, node.dataInterface());
        return port.isPresent() ? port.get() : null;

    }

    protected PortNumber tunnelPort(DeviceId deviceId) {
        Optional<PortNumber> port = getPortNumber(deviceId, DEFAULT_TUNNEL);
        return port.isPresent() ? port.get() : null;
    }

    protected PortNumber hostManagementPort(DeviceId deviceId) {
        CordVtnNode node = nodeService.node(deviceId);
        if (node == null) {
            log.debug("Failed to get node for {}", deviceId);
            return null;
        }

        if (node.hostManagementInterface() != null) {
            Optional<PortNumber> port =
                    getPortNumber(deviceId, node.hostManagementInterface());
            return port.isPresent() ? port.get() : null;
        } else {
            return null;
        }
    }

    protected ExtensionTreatment tunnelDstTreatment(DeviceId deviceId, Ip4Address remoteIp) {
        Device device = deviceService.getDevice(deviceId);
        if (device != null && !device.is(ExtensionTreatmentResolver.class)) {
            log.error("The extension treatment is not supported");
            return null;
        }

        ExtensionTreatmentResolver resolver = device.as(ExtensionTreatmentResolver.class);
        ExtensionTreatment treatment = resolver.getExtensionInstruction(NICIRA_SET_TUNNEL_DST.type());
        try {
            treatment.setPropertyValue(PROPERTY_TUNNEL_DST, remoteIp);
            return treatment;
        } catch (ExtensionPropertyException e) {
            log.warn("Failed to get tunnelDst extension treatment for {}", deviceId);
            return null;
        }
    }

    protected IpAddress dataIp(DeviceId deviceId) {
        CordVtnNode node = nodeService.node(deviceId);
        if (node == null) {
            log.debug("Failed to get node for {}", deviceId);
            return null;
        }
        return node.dataIp().ip();
    }

    private Optional<PortNumber> getPortNumber(DeviceId deviceId, String portName) {
        PortNumber port = deviceService.getPorts(deviceId).stream()
                .filter(p -> p.annotations().value(PORT_NAME).equals(portName) &&
                        p.isEnabled())
                .map(Port::number)
                .findAny()
                .orElse(null);
        return Optional.ofNullable(port);
    }

    private class InternalHostListener implements HostListener {

        @Override
        public void event(HostEvent event) {
            eventExecutor.execute(() -> handle(event));
        }

        private void handle(HostEvent event) {
            Host host = event.subject();
            if (!mastershipService.isLocalMaster(host.location().deviceId())) {
                // do not allow to proceed without mastership
                return;
            }

            Instance instance = Instance.of(host);
            if (!netTypes.isEmpty() && !netTypes.contains(instance.netType())) {
                // not my service network instance, do nothing
                return;
            }

            switch (event.type()) {
            case HOST_UPDATED:
                instanceUpdated(instance);
                break;
            case HOST_ADDED:
                instanceDetected(instance);
                break;
            case HOST_REMOVED:
                instanceRemoved(instance);
                break;
            default:
                break;
            }
        }
    }
}
