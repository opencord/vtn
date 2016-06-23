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
import org.opencord.cordvtn.api.CordVtnConfig;
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
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.DefaultHostDescription;
import org.onosproject.net.host.HostDescription;
import org.onosproject.net.host.HostProvider;
import org.onosproject.net.host.HostProviderRegistry;
import org.onosproject.net.host.HostProviderService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.xosclient.api.OpenStackAccess;
import org.onosproject.xosclient.api.VtnPort;
import org.onosproject.xosclient.api.VtnPortApi;
import org.onosproject.xosclient.api.VtnService;
import org.onosproject.xosclient.api.VtnServiceApi;
import org.onosproject.xosclient.api.VtnServiceId;
import org.onosproject.xosclient.api.XosAccess;
import org.onosproject.xosclient.api.XosClientService;
import org.opencord.cordvtn.api.InstanceService;
import org.slf4j.Logger;

import java.util.Date;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.dhcp.IpAssignment.AssignmentStatus.Option_RangeNotEnforced;
import static org.onosproject.xosclient.api.VtnServiceApi.NetworkType.MANAGEMENT;
import static org.opencord.cordvtn.api.Constants.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Adds or removes instances to network services.
 */
@Component(immediate = true)
@Service
public class InstanceManager extends AbstractProvider implements HostProvider,
        InstanceService {

    protected final Logger log = getLogger(getClass());

    private static final Ip4Address DEFAULT_DNS = Ip4Address.valueOf("8.8.8.8");
    private static final int DHCP_INFINITE_LEASE = -1;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostProviderRegistry hostProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DhcpService dhcpService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected XosClientService xosClient;

    private final ConfigFactory configFactory =
            new ConfigFactory(SubjectFactories.APP_SUBJECT_FACTORY, CordVtnConfig.class, "cordvtn") {
                @Override
                public CordVtnConfig createConfig() {
                    return new CordVtnConfig();
                }
            };

    private final ExecutorService eventExecutor =
            newSingleThreadScheduledExecutor(groupedThreads("onos/cordvtn-instance", "event-handler"));
    private final NetworkConfigListener configListener = new InternalConfigListener();

    private ApplicationId appId;
    private HostProviderService hostProvider;
    private XosAccess xosAccess = null;
    private OpenStackAccess osAccess = null;

    /**
     * Creates an cordvtn host location provider.
     */
    public InstanceManager() {
        super(new ProviderId("host", CORDVTN_APP_ID));
    }

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(CORDVTN_APP_ID);

        hostProvider = hostProviderRegistry.register(this);
        configRegistry.registerConfigFactory(configFactory);
        configRegistry.addListener(configListener);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        hostProviderRegistry.unregister(this);

        configRegistry.unregisterConfigFactory(configFactory);
        configRegistry.removeListener(configListener);

        eventExecutor.shutdown();
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

        VtnPort vtnPort = getVtnPort(port.annotations().value(PORT_NAME));
        if (vtnPort == null) {
            return;
        }

        VtnService vtnService = getVtnService(vtnPort.serviceId());
        if (vtnService == null) {
            return;
        }

        // register DHCP lease for the new instance
        registerDhcpLease(vtnPort.mac(), vtnPort.ip().getIp4Address(), vtnService);

        // Added CREATE_TIME intentionally to trigger HOST_UPDATED event for the
        // existing instances.
        DefaultAnnotations.Builder annotations = DefaultAnnotations.builder()
                .set(Instance.SERVICE_TYPE, vtnService.serviceType().toString())
                .set(Instance.SERVICE_ID, vtnPort.serviceId().id())
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

    private void registerDhcpLease(MacAddress macAddr, Ip4Address ipAddr, VtnService service) {
        Ip4Address broadcast = Ip4Address.makeMaskedAddress(
                ipAddr,
                service.subnet().prefixLength());

        IpAssignment.Builder ipBuilder = IpAssignment.builder()
                .ipAddress(ipAddr)
                .leasePeriod(DHCP_INFINITE_LEASE)
                .timestamp(new Date())
                .subnetMask(Ip4Address.makeMaskPrefix(service.subnet().prefixLength()))
                .broadcast(broadcast)
                .domainServer(DEFAULT_DNS)
                .assignmentStatus(Option_RangeNotEnforced);

        if (service.networkType() != MANAGEMENT) {
            ipBuilder = ipBuilder.routerAddress(service.serviceIp().getIp4Address());
        }

        log.debug("Set static DHCP mapping for {} {}", macAddr, ipAddr);
        dhcpService.setStaticMapping(macAddr, ipBuilder.build());
    }

    private VtnService getVtnService(VtnServiceId serviceId) {
        checkNotNull(osAccess, ERROR_OPENSTACK_ACCESS);
        checkNotNull(xosAccess, ERROR_XOS_ACCESS);

        // TODO remove openstack access when XOS provides all information
        VtnServiceApi serviceApi = xosClient.getClient(xosAccess).vtnService();
        VtnService service = serviceApi.service(serviceId, osAccess);
        if (service == null) {
            log.warn("Failed to get VtnService for {}", serviceId);
        }
        return service;
    }

    private VtnPort getVtnPort(String portName) {
        checkNotNull(osAccess, ERROR_OPENSTACK_ACCESS);
        checkNotNull(xosAccess, ERROR_XOS_ACCESS);

        // TODO remove openstack access when XOS provides all information
        VtnPortApi portApi = xosClient.getClient(xosAccess).vtnPort();
        VtnPort vtnPort = portApi.vtnPort(portName, osAccess);
        if (vtnPort == null) {
            log.warn("Failed to get port information of {}", portName);
        }
        return vtnPort;
    }

    private void readConfiguration() {
        CordVtnConfig config = configRegistry.getConfig(appId, CordVtnConfig.class);
        if (config == null) {
            log.debug("No configuration found");
            return;
        }

        log.info("Load CORD-VTN configurations");
        xosAccess = config.xosAccess();
        osAccess = config.openstackAccess();
    }

    private class InternalConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            if (!event.configClass().equals(CordVtnConfig.class)) {
                return;
            }

            switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                    readConfiguration();
                    break;
                default:
                    break;
            }
        }
    }
}
