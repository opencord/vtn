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

import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.store.AbstractStore;
import org.onosproject.store.service.MapEvent;
import org.onosproject.store.service.MapEventListener;
import org.onosproject.store.service.Versioned;
import org.opencord.cordvtn.api.CordVtnStore;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.cordvtn.api.AddressPair;
import org.opencord.cordvtn.api.CordVtnStoreDelegate;
import org.opencord.cordvtn.api.Dependency;
import org.opencord.cordvtn.api.NetworkId;
import org.opencord.cordvtn.api.PortId;
import org.opencord.cordvtn.api.ProviderNetwork;
import org.opencord.cordvtn.api.SegmentId;
import org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType;
import org.opencord.cordvtn.api.SubnetId;
import org.opencord.cordvtn.api.VtnNetwork;
import org.opencord.cordvtn.api.VtnNetworkEvent;
import org.opencord.cordvtn.api.VtnPort;
import org.openstack4j.model.network.IPVersionType;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.NetworkType;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.State;
import org.openstack4j.model.network.Subnet;
import org.openstack4j.openstack.networking.domain.NeutronAllowedAddressPair;
import org.openstack4j.openstack.networking.domain.NeutronExtraDhcpOptCreate;
import org.openstack4j.openstack.networking.domain.NeutronHostRoute;
import org.openstack4j.openstack.networking.domain.NeutronIP;
import org.openstack4j.openstack.networking.domain.NeutronNetwork;
import org.openstack4j.openstack.networking.domain.NeutronPool;
import org.openstack4j.openstack.networking.domain.NeutronPort;
import org.openstack4j.openstack.networking.domain.NeutronSubnet;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.cordvtn.api.Constants.CORDVTN_APP_ID;
import static org.opencord.cordvtn.api.VtnNetworkEvent.Type.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manages the inventory of VTN networks using a {@code ConsistentMap}.
 */
@Component(immediate = true)
@Service
public class DistributedCordVtnStore extends AbstractStore<VtnNetworkEvent, CordVtnStoreDelegate>
        implements CordVtnStore {

    protected final Logger log = getLogger(getClass());

    private static final String ERR_SYNC = "VTN store is out of sync: ";
    private static final String ERR_NOT_FOUND = " does not exist";
    private static final String ERR_DUPLICATE = " already exists with different properties";

    private static final KryoNamespace SERIALIZER_SERVICE = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(VtnNetwork.class)
            .register(NetworkId.class)
            .register(SegmentId.class)
            .register(ServiceNetworkType.class)
            .register(ProviderNetwork.class)
            .register(Dependency.Type.class)
            .register(VtnPort.class)
            .register(PortId.class)
            .register(AddressPair.class)
            .build();

    // Use Neutron data model until we need our own abstraction of virtual networks
    private static final KryoNamespace SERIALIZER_NEUTRON = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(Network.class)
            .register(NetworkId.class)
            .register(NeutronNetwork.class)
            .register(State.class)
            .register(NetworkType.class)
            .register(Port.class)
            .register(PortId.class)
            .register(NeutronPort.class)
            .register(NeutronIP.class)
            .register(NeutronAllowedAddressPair.class)
            .register(NeutronExtraDhcpOptCreate.class)
            .register(Subnet.class)
            .register(SubnetId.class)
            .register(NeutronSubnet.class)
            .register(NeutronPool.class)
            .register(NeutronHostRoute.class)
            .register(IPVersionType.class)
            .build();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    private final MapEventListener<PortId, VtnPort> vtnPortListener = new VtnPortMapListener();
    private final MapEventListener<NetworkId, VtnNetwork> vtnNetworkListener = new VtnNetworkMapListener();
    private final ExecutorService eventExecutor = newSingleThreadExecutor(
            groupedThreads(this.getClass().getSimpleName(), "event-handler", log));

    private ConsistentMap<NetworkId, VtnNetwork> vtnNetworkStore;
    private ConsistentMap<PortId, VtnPort> vtnPortStore;
    private ConsistentMap<NetworkId, Network> networkStore;
    private ConsistentMap<SubnetId, Subnet> subnetStore;
    private ConsistentMap<PortId, Port> portStore;

    @Activate
    protected void activate() {
        ApplicationId appId = coreService.registerApplication(CORDVTN_APP_ID);

        vtnNetworkStore = storageService.<NetworkId, VtnNetwork>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_SERVICE))
                .withName("cordvtn-vtnnetstore")
                .withApplicationId(appId)
                .build();
        vtnNetworkStore.addListener(vtnNetworkListener);

        vtnPortStore = storageService.<PortId, VtnPort>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_SERVICE))
                .withName("cordvtn-vtnportstore")
                .withApplicationId(appId)
                .build();
        vtnPortStore.addListener(vtnPortListener);

        networkStore = storageService.<NetworkId, Network>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_NEUTRON))
                .withName("cordvtn-networkstore")
                .withApplicationId(appId)
                .build();

        portStore = storageService.<PortId, Port>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_NEUTRON))
                .withName("cordvtn-portstore")
                .withApplicationId(appId)
                .build();

        subnetStore = storageService.<SubnetId, Subnet>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_NEUTRON))
                .withName("cordvtn-subnetstore")
                .withApplicationId(appId)
                .build();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        vtnNetworkStore.removeListener(vtnNetworkListener);
        vtnPortStore.removeListener(vtnPortListener);

        log.info("Stopped");
    }

    @Override
    public void createVtnNetwork(VtnNetwork vtnNet) {
        vtnNetworkStore.compute(vtnNet.id(), (id, existing) -> {
            final String error = ERR_SYNC + vtnNet.id().id() + ERR_DUPLICATE;
            checkArgument(existing == null || existing.equals(vtnNet), error);
            return vtnNet;
        });
    }

    @Override
    public void updateVtnNetwork(VtnNetwork vtnNet) {
        vtnNetworkStore.compute(vtnNet.id(), (id, existing) -> {
            final String error = ERR_SYNC + vtnNet.id().id() + ERR_NOT_FOUND;
            checkArgument(existing != null, ERR_SYNC + error);
            return vtnNet;
        });
    }

    @Override
    public void removeVtnNetwork(NetworkId netId) {
        synchronized (this) {
            // remove any dependencies that this network involved in
            vtnNetworkStore.computeIfPresent(netId, (id, existing) ->
                    VtnNetwork.builder(existing)
                            .providers(ImmutableSet.of()).build()
            );
            getSubscribers(netId).stream().forEach(subs ->
                vtnNetworkStore.computeIfPresent(subs.id(), (id, existing) ->
                    VtnNetwork.builder(existing)
                            .delProvider(netId).build())
            );
            vtnNetworkStore.remove(netId);
        }
    }

    @Override
    public VtnNetwork vtnNetwork(NetworkId netId) {
        Versioned<VtnNetwork> versioned = vtnNetworkStore.get(netId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<VtnNetwork> vtnNetworks() {
        return vtnNetworkStore.values().stream().map(Versioned::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void createVtnPort(VtnPort vtnPort) {
        vtnPortStore.compute(vtnPort.id(), (id, existing) -> {
            final String error = ERR_SYNC + vtnPort.id().id() + ERR_DUPLICATE;
            checkArgument(existing == null || existing.equals(vtnPort), error);
            return vtnPort;
        });
    }

    @Override
    public void updateVtnPort(VtnPort vtnPort) {
        vtnPortStore.compute(vtnPort.id(), (id, existing) -> {
            final String error = ERR_SYNC + vtnPort.id().id() + ERR_NOT_FOUND;
            checkArgument(existing != null, ERR_SYNC + error);
            return vtnPort;
        });
    }

    @Override
    public void removeVtnPort(PortId portId) {
        vtnPortStore.remove(portId);
    }

    @Override
    public VtnPort vtnPort(PortId portId) {
        Versioned<VtnPort> versioned = vtnPortStore.get(portId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<VtnPort> vtnPorts() {
        return vtnPortStore.values().stream().map(Versioned::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void createNetwork(Network net) {
        networkStore.compute(NetworkId.of(net.getId()), (id, existing) -> {
            final String error = ERR_SYNC + net.getId() + ERR_DUPLICATE;
            checkArgument(existing == null || net.equals(existing), error);
            return net;
        });
    }

    @Override
    public void updateNetwork(Network net) {
        networkStore.compute(NetworkId.of(net.getId()), (id, existing) -> {
            final String error = ERR_SYNC + net.getId() + ERR_NOT_FOUND;
            checkArgument(existing != null, ERR_SYNC + error);
            return net;
        });
    }

    @Override
    public void removeNetwork(NetworkId netId) {
        networkStore.remove(netId);
    }

    @Override
    public Network network(NetworkId netId) {
        Versioned<Network> versioned = networkStore.get(netId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<Network> networks() {
        return networkStore.values().stream().map(Versioned::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void createPort(Port port) {
        portStore.compute(PortId.of(port.getId()), (id, existing) -> {
            final String error = ERR_SYNC + port.getId() + ERR_DUPLICATE;
            checkArgument(existing == null || port.equals(existing), error);
            return port;
        });
    }

    @Override
    public void updatePort(Port port) {
        portStore.compute(PortId.of(port.getId()), (id, existing) -> {
            final String error = ERR_SYNC + port.getId() + ERR_NOT_FOUND;
            checkArgument(existing != null, ERR_SYNC + error);
            return port;
        });
    }

    @Override
    public void removePort(PortId portId) {
        portStore.remove(portId);
    }

    @Override
    public Port port(PortId portId) {
        Versioned<Port> versioned = portStore.get(portId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<Port> ports() {
        return portStore.values().stream().map(Versioned::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void createSubnet(Subnet subnet) {
        subnetStore.compute(SubnetId.of(subnet.getId()), (id, existing) -> {
            final String error = ERR_SYNC + subnet.getId() + ERR_DUPLICATE;
            checkArgument(existing == null || subnet.equals(existing), error);
            return subnet;
        });
    }

    @Override
    public void updateSubnet(Subnet subnet) {
        subnetStore.compute(SubnetId.of(subnet.getId()), (id, existing) -> {
            final String error = ERR_SYNC + subnet.getId() + ERR_NOT_FOUND;
            checkArgument(existing != null, ERR_SYNC + error);
            return subnet;
        });
    }

    @Override
    public void removeSubnet(SubnetId subnetId) {
        subnetStore.remove(subnetId);
    }

    @Override
    public Subnet subnet(SubnetId subnetId) {
        Versioned<Subnet> versioned = subnetStore.get(subnetId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<Subnet> subnets() {
        return subnetStore.values().stream().map(Versioned::value)
                .collect(Collectors.toSet());
    }

    private Set<VtnNetwork> getSubscribers(NetworkId netId) {
        return vtnNetworks().stream().filter(net -> net.isProvider(netId))
                .collect(Collectors.toSet());
    }

    private class VtnNetworkMapListener implements MapEventListener<NetworkId, VtnNetwork> {

        @Override
        public void event(MapEvent<NetworkId, VtnNetwork> event) {
            switch (event.type()) {
                case UPDATE:
                    log.debug("VTN network updated {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new VtnNetworkEvent(
                                VTN_NETWORK_UPDATED,
                                event.newValue().value()));
                    });
                    break;
                case INSERT:
                    log.debug("VTN network created {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new VtnNetworkEvent(
                                VTN_NETWORK_CREATED,
                                event.newValue().value()));
                    });
                    break;
                case REMOVE:
                    log.debug("VTN network removed {}", event.oldValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new VtnNetworkEvent(
                                VTN_NETWORK_REMOVED,
                                event.oldValue().value()));
                    });
                    break;
                default:
                    log.error("Unsupported event type");
                    break;
            }
        }
    }

    private class VtnPortMapListener implements MapEventListener<PortId, VtnPort> {

        @Override
        public void event(MapEvent<PortId, VtnPort> event) {
            switch (event.type()) {
                case UPDATE:
                    log.debug("VTN port updated {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new VtnNetworkEvent(
                                VTN_PORT_UPDATED,
                                vtnNetwork(event.newValue().value().netId()),
                                event.newValue().value()));
                    });
                    break;
                case INSERT:
                    log.debug("VTN port created {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new VtnNetworkEvent(
                                VTN_PORT_CREATED,
                                vtnNetwork(event.newValue().value().netId()),
                                event.newValue().value()));
                    });
                    break;
                case REMOVE:
                    log.debug("VTN port removed {}", event.oldValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new VtnNetworkEvent(
                                VTN_PORT_REMOVED,
                                vtnNetwork(event.oldValue().value().netId()),
                                event.oldValue().value()));
                    });
                    break;
                default:
                    log.error("Unsupported event type");
                    break;
            }
        }
    }
}
