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
package org.opencord.cordvtn.impl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.ListenerRegistry;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.net.host.HostService;
import org.opencord.cordvtn.api.Constants;
import org.opencord.cordvtn.api.CordVtnConfig;
import org.opencord.cordvtn.api.core.ServiceNetworkAdminService;
import org.opencord.cordvtn.api.core.ServiceNetworkEvent;
import org.opencord.cordvtn.api.core.ServiceNetworkListener;
import org.opencord.cordvtn.api.core.ServiceNetworkService;
import org.opencord.cordvtn.api.core.ServiceNetworkStore;
import org.opencord.cordvtn.api.core.ServiceNetworkStoreDelegate;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.SegmentId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.rest.XosVtnNetworkingClient;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.identity.Access;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides implementation of administering and interfacing service network and port.
 */
@Component(immediate = true)
@Service
public class ServiceNetworkManager extends ListenerRegistry<ServiceNetworkEvent, ServiceNetworkListener>
        implements ServiceNetworkAdminService, ServiceNetworkService {

    protected final Logger log = getLogger(getClass());

    private static final String MSG_SERVICE_NET  = "Service network %s %s";
    private static final String MSG_SERVICE_PORT = "Service port %s %s";
    private static final String MSG_PROVIDER_NET = "Provider network %s %s";
    private static final String MSG_CREATED = "created";
    private static final String MSG_UPDATED = "updated";
    private static final String MSG_REMOVED = "removed";

    private static final String ERR_NULL_SERVICE_NET  = "Service network cannot be null";
    private static final String ERR_NULL_SERVICE_NET_ID  = "Service network ID cannot be null";
    private static final String ERR_NULL_SERVICE_NET_TYPE  = "Service network type cannot be null";
    private static final String ERR_NULL_SERVICE_PORT = "Service port cannot be null";
    private static final String ERR_NULL_SERVICE_PORT_ID = "Service port ID cannot be null";
    private static final String ERR_NULL_SERVICE_PORT_NAME = "Service port name cannot be null";
    private static final String ERR_NULL_SERVICE_PORT_NET_ID = "Service port network ID cannot be null";

    private static final String ERR_NOT_FOUND = " does not exist";
    private static final String ERR_IN_USE = " still in use";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceNetworkStore snetStore;

    private ApplicationId appId;

    // TODO add cordvtn config service and move this
    private static final Class<CordVtnConfig> CONFIG_CLASS = CordVtnConfig.class;
    private final ConfigFactory configFactory =
            new ConfigFactory<ApplicationId, CordVtnConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY, CONFIG_CLASS, "cordvtn") {
                @Override
                public CordVtnConfig createConfig() {
                    return new CordVtnConfig();
                }
            };

    private final ServiceNetworkStoreDelegate delegate = new InternalServiceNetworkStoreDelegate();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(Constants.CORDVTN_APP_ID);
        configRegistry.registerConfigFactory(configFactory);
        snetStore.setDelegate(delegate);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        configRegistry.unregisterConfigFactory(configFactory);
        snetStore.unsetDelegate(delegate);
        log.info("Stopped");
    }

    @Override
    public void purgeStates() {
        snetStore.clear();
    }

    @Override
    public void syncXosState() {
        CordVtnConfig config = configRegistry.getConfig(appId, CONFIG_CLASS);
        if (config == null) {
            return;
        }
        syncXosState(config.getXosEndpoint(), config.getXosUsername(), config.getXosPassword());
    }

    @Override
    public void syncXosState(String endpoint, String user, String password) {
        checkNotNull(endpoint);
        checkNotNull(user);
        checkNotNull(password);

        XosVtnNetworkingClient client = XosVtnNetworkingClient.builder()
                .endpoint(endpoint)
                .user(user)
                .password(password)
                .build();

        client.requestSync();
    }

    @Override
    public void syncNeutronState() {
        CordVtnConfig config = configRegistry.getConfig(appId, CONFIG_CLASS);
        if (config == null) {
            return;
        }
        syncNeutronState(config.getOpenstackEndpoint(), config.getOpenstackTenant(),
                config.getOpenstackUser(), config.getOpenstackPassword());
    }

    @Override
    public void syncNeutronState(String endpoint, String tenant, String user, String password) {
        Access osAccess;
        try {
            osAccess = OSFactory.builder()
                    .endpoint(endpoint)
                    .tenantName(tenant)
                    .credentials(user, password)
                    .authenticate()
                    .getAccess();
        } catch (AuthenticationException e) {
            log.warn("Authentication failed");
            return;
        } catch (Exception e) {
            log.warn("OpenStack service endpoint is unreachable");
            return;
        }

        OSClient osClient = OSFactory.clientFromAccess(osAccess);
        osClient.networking().network().list().forEach(osNet -> {
            ServiceNetwork snet = DefaultServiceNetwork.builder()
                    .id(NetworkId.of(osNet.getId()))
                    .name(osNet.getName())
                    .type(ServiceNetwork.NetworkType.PRIVATE)
                    .segmentId(SegmentId.of(Long.valueOf(osNet.getProviderSegID())))
                    .build();
            if (serviceNetwork(snet.id()) != null) {
                updateServiceNetwork(snet);
            } else {
                createServiceNetwork(snet);
            }
        });

        osClient.networking().subnet().list().forEach(osSubnet -> {
            ServiceNetwork snet = DefaultServiceNetwork.builder()
                    .id(NetworkId.of(osSubnet.getNetworkId()))
                    .subnet(IpPrefix.valueOf(osSubnet.getCidr()))
                    .serviceIp(IpAddress.valueOf(osSubnet.getGateway()))
                    .build();
            updateServiceNetwork(snet);
        });

        osClient.networking().port().list().forEach(osPort -> {
            ServicePort.Builder sportBuilder = DefaultServicePort.builder()
                    .id(PortId.of(osPort.getId()))
                    .name("tap" + osPort.getId().substring(0, 11))
                    .networkId(NetworkId.of(osPort.getNetworkId()));

            if (osPort.getMacAddress() != null) {
                sportBuilder.mac(MacAddress.valueOf(osPort.getMacAddress()));
            }
            if (!osPort.getFixedIps().isEmpty()) {
                sportBuilder.ip(IpAddress.valueOf(
                        osPort.getFixedIps().iterator().next().getIpAddress()));
            }
            ServicePort sport = sportBuilder.build();
            if (servicePort(sport.id()) != null) {
                updateServicePort(sport);
            } else {
                createServicePort(sport);
            }
        });
    }

    @Override
    public ServiceNetwork serviceNetwork(NetworkId netId) {
        checkNotNull(netId, ERR_NULL_SERVICE_NET_ID);
        return snetStore.serviceNetwork(netId);
    }

    @Override
    public Set<ServiceNetwork> serviceNetworks() {
        return snetStore.serviceNetworks();
    }

    @Override
    public void createServiceNetwork(ServiceNetwork snet) {
        checkNotNull(snet, ERR_NULL_SERVICE_NET);
        checkNotNull(snet.id(), ERR_NULL_SERVICE_NET_ID);
        checkNotNull(snet.type(), ERR_NULL_SERVICE_NET_TYPE);
        synchronized (this) {
            snet.providers().keySet().forEach(provider -> {
                if (snetStore.serviceNetwork(provider) == null) {
                    final String error = String.format(
                            MSG_PROVIDER_NET, provider.id(), ERR_NOT_FOUND);
                    throw new IllegalStateException(error);
                }
            });
            snetStore.createServiceNetwork(snet);
            log.info(String.format(MSG_SERVICE_NET, snet.name(), MSG_CREATED));
        }
    }

    @Override
    public void updateServiceNetwork(ServiceNetwork snet) {
        checkNotNull(snet, ERR_NULL_SERVICE_NET);
        checkNotNull(snet.id(), ERR_NULL_SERVICE_NET_ID);
        synchronized (this) {
            ServiceNetwork existing = snetStore.serviceNetwork(snet.id());
            if (existing == null) {
                final String error = String.format(
                        MSG_SERVICE_NET, snet.id(), ERR_NOT_FOUND);
                throw new IllegalStateException(error);
            }
            // TODO do not allow service type update if the network in use
            snetStore.updateServiceNetwork(DefaultServiceNetwork.builder(existing, snet).build());
            log.info(String.format(MSG_SERVICE_NET, existing.name(), MSG_UPDATED));
        }
    }

    @Override
    public void removeServiceNetwork(NetworkId netId) {
        checkNotNull(netId, ERR_NULL_SERVICE_NET_ID);
        synchronized (this) {
            if (isNetworkInUse(netId)) {
                final String error = String.format(MSG_SERVICE_NET, netId, ERR_IN_USE);
                throw new IllegalStateException(error);
            }
            // remove dependencies on this network first
            serviceNetworks().stream().filter(n -> isProvider(n, netId)).forEach(n -> {
                Map<NetworkId, DependencyType> newProviders = Maps.newHashMap(n.providers());
                newProviders.remove(netId);
                ServiceNetwork updated = DefaultServiceNetwork.builder(n)
                        .providers(newProviders)
                        .build();
                snetStore.updateServiceNetwork(updated);
            });
            ServiceNetwork snet = snetStore.removeServiceNetwork(netId);
            log.info(String.format(MSG_SERVICE_NET, snet.name(), MSG_REMOVED));
        }
    }

    @Override
    public ServicePort servicePort(PortId portId) {
        checkNotNull(portId, ERR_NULL_SERVICE_PORT_ID);
        return snetStore.servicePort(portId);
    }

    @Override
    public Set<ServicePort> servicePorts() {
        return snetStore.servicePorts();
    }

    @Override
    public Set<ServicePort> servicePorts(NetworkId netId) {
        Set<ServicePort> sports = snetStore.servicePorts().stream()
                .filter(p -> Objects.equals(p.networkId(), netId))
                .collect(Collectors.toSet());
        return ImmutableSet.copyOf(sports);
    }

    @Override
    public void createServicePort(ServicePort sport) {
        checkNotNull(sport, ERR_NULL_SERVICE_PORT);
        checkNotNull(sport.id(), ERR_NULL_SERVICE_PORT_ID);
        checkNotNull(sport.id(), ERR_NULL_SERVICE_PORT_NAME);
        checkNotNull(sport.networkId(), ERR_NULL_SERVICE_PORT_NET_ID);
        synchronized (this) {
            ServiceNetwork existing = snetStore.serviceNetwork(sport.networkId());
            if (existing == null) {
                final String error = String.format(
                        MSG_SERVICE_NET, sport.networkId(), ERR_NOT_FOUND);
                throw new IllegalStateException(error);
            }
            snetStore.createServicePort(sport);
            log.info(String.format(MSG_SERVICE_PORT, sport.id(), MSG_CREATED));
        }
    }

    @Override
    public void updateServicePort(ServicePort sport) {
        checkNotNull(sport, ERR_NULL_SERVICE_PORT);
        checkNotNull(sport.id(), ERR_NULL_SERVICE_PORT_ID);
        synchronized (this) {
            ServicePort existing = snetStore.servicePort(sport.id());
            if (existing == null) {
                final String error = String.format(
                        MSG_SERVICE_PORT, sport.id(), ERR_NOT_FOUND);
                throw new IllegalStateException(error);
            }
            snetStore.updateServicePort(DefaultServicePort.builder(existing, sport).build());
            log.info(String.format(MSG_SERVICE_PORT, sport.id(), MSG_UPDATED));
        }
    }

    @Override
    public void removeServicePort(PortId portId) {
        checkNotNull(portId, ERR_NULL_SERVICE_PORT_ID);
        synchronized (this) {
            if (isPortInUse(portId)) {
                final String error = String.format(MSG_SERVICE_PORT, portId, ERR_IN_USE);
                throw new IllegalStateException(error);
            }
            snetStore.removeServicePort(portId);
            log.info(String.format(MSG_SERVICE_PORT, portId, MSG_REMOVED));
        }
    }

    /**
     * Returns if the given target network is a provider of the given network.
     *
     * @param snet     service network
     * @param targetId target network
     * @return true if the service network is a provider of the target network
     */
    private boolean isProvider(ServiceNetwork snet, NetworkId targetId) {
        checkNotNull(snet);
        return snet.providers().keySet().contains(targetId);
    }

    private boolean isNetworkInUse(NetworkId netId) {
        // TODO use instance service to see if there's running instance for the network
        return !servicePorts(netId).isEmpty();
    }

    private boolean isPortInUse(PortId portId) {
        ServicePort sport = servicePort(portId);
        if (sport == null) {
            final String error = String.format(MSG_SERVICE_PORT, portId, ERR_NOT_FOUND);
            throw new IllegalStateException(error);
        }
        // TODO use instance service to see if there's running instance for the port
        Host host = hostService.getHost(HostId.hostId(sport.mac()));
        return host != null;
    }

    private class InternalServiceNetworkStoreDelegate implements ServiceNetworkStoreDelegate {

        @Override
        public void notify(ServiceNetworkEvent event) {
            if (event != null) {
                log.trace("send service network event {}", event);
                process(event);
            }
        }
    }
}
