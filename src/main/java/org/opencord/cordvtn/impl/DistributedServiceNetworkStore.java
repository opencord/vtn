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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.store.AbstractStore;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.MapEvent;
import org.onosproject.store.service.MapEventListener;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.Versioned;
import org.opencord.cordvtn.api.core.ServiceNetworkEvent;
import org.opencord.cordvtn.api.core.ServiceNetworkStore;
import org.opencord.cordvtn.api.core.ServiceNetworkStoreDelegate;
import org.opencord.cordvtn.api.net.AddressPair;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.Provider;
import org.opencord.cordvtn.api.net.SegmentId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType;
import org.opencord.cordvtn.api.net.ServicePort;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.cordvtn.api.Constants.CORDVTN_APP_ID;
import static org.opencord.cordvtn.api.core.ServiceNetworkEvent.Type.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manages the inventory of VTN networks using a {@code ConsistentMap}.
 */
@Component(immediate = true)
@Service
public class DistributedServiceNetworkStore extends AbstractStore<ServiceNetworkEvent, ServiceNetworkStoreDelegate>
        implements ServiceNetworkStore {

    protected final Logger log = getLogger(getClass());

    private static final String ERR_NOT_FOUND = " does not exist";
    private static final String ERR_DUPLICATE = " already exists";

    private static final KryoNamespace SERIALIZER_SERVICE = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(ServiceNetwork.class)
            .register(DefaultServiceNetwork.class)
            .register(NetworkId.class)
            .register(SegmentId.class)
            .register(ServiceNetwork.NetworkType.class)
            .register(DependencyType.class)
            .register(ServicePort.class)
            .register(DefaultServicePort.class)
            .register(PortId.class)
            .register(AddressPair.class)
            .register(Collections.EMPTY_MAP.getClass())
            .register(Collections.EMPTY_SET.getClass())
            .build();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    private final ExecutorService eventExecutor = newSingleThreadExecutor(
            groupedThreads(this.getClass().getSimpleName(), "event-handler", log));
    private final MapEventListener<PortId, ServicePort> servicePortListener =
            new ServicePortMapListener();
    private final MapEventListener<NetworkId, ServiceNetwork> serviceNetworkListener =
            new ServiceNetworkMapListener();

    private ConsistentMap<NetworkId, ServiceNetwork> serviceNetworkStore;
    private ConsistentMap<PortId, ServicePort> servicePortStore;

    @Activate
    protected void activate() {
        ApplicationId appId = coreService.registerApplication(CORDVTN_APP_ID);
        serviceNetworkStore = storageService.<NetworkId, ServiceNetwork>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_SERVICE))
                .withName("cordvtn-servicenetstore")
                .withApplicationId(appId)
                .build();
        serviceNetworkStore.addListener(serviceNetworkListener);

        servicePortStore = storageService.<PortId, ServicePort>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_SERVICE))
                .withName("cordvtn-serviceportstore")
                .withApplicationId(appId)
                .build();
        servicePortStore.addListener(servicePortListener);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        serviceNetworkStore.removeListener(serviceNetworkListener);
        servicePortStore.removeListener(servicePortListener);

        log.info("Stopped");
    }

    @Override
    public void clear() {
        synchronized (this) {
            serviceNetworkStore.clear();
            servicePortStore.clear();
        }
    }

    @Override
    public void createServiceNetwork(ServiceNetwork snet) {
        serviceNetworkStore.compute(snet.id(), (id, existing) -> {
            final String error = snet.name() + ERR_DUPLICATE;
            checkArgument(existing == null || existing.equals(snet), error);
            return snet;
        });
    }

    @Override
    public void updateServiceNetwork(ServiceNetwork snet) {
        serviceNetworkStore.compute(snet.id(), (id, existing) -> {
            final String error = snet.name() + ERR_NOT_FOUND;
            checkArgument(existing != null, error);
            return snet;
        });
    }

    @Override
    public ServiceNetwork removeServiceNetwork(NetworkId netId) {
        synchronized (this) {
            Versioned<ServiceNetwork> snet = serviceNetworkStore.remove(netId);
            return snet == null ? null : snet.value();
        }
    }

    @Override
    public ServiceNetwork serviceNetwork(NetworkId netId) {
        Versioned<ServiceNetwork> versioned = serviceNetworkStore.get(netId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<ServiceNetwork> serviceNetworks() {
        Set<ServiceNetwork> snets = serviceNetworkStore.values().stream()
                .map(Versioned::value)
                .collect(Collectors.toSet());
        return ImmutableSet.copyOf(snets);
    }

    @Override
    public void createServicePort(ServicePort sport) {
        servicePortStore.compute(sport.id(), (id, existing) -> {
            final String error = sport.id().id() + ERR_DUPLICATE;
            checkArgument(existing == null || existing.equals(sport), error);
            return sport;
        });
    }

    @Override
    public void updateServicePort(ServicePort sport) {
        servicePortStore.compute(sport.id(), (id, existing) -> {
            final String error = sport.id().id() + ERR_NOT_FOUND;
            checkArgument(existing != null, error);
            return sport;
        });
    }

    @Override
    public ServicePort removeServicePort(PortId portId) {
        Versioned<ServicePort> sport = servicePortStore.remove(portId);
        return sport.value();
    }

    @Override
    public ServicePort servicePort(PortId portId) {
        Versioned<ServicePort> versioned = servicePortStore.get(portId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<ServicePort> servicePorts() {
        Set<ServicePort> sports = servicePortStore.values().stream()
                .map(Versioned::value)
                .collect(Collectors.toSet());
        return ImmutableSet.copyOf(sports);
    }

    private class ServiceNetworkMapListener implements MapEventListener<NetworkId, ServiceNetwork> {

        @Override
        public void event(MapEvent<NetworkId, ServiceNetwork> event) {
            switch (event.type()) {
                case UPDATE:
                    log.debug("Service network updated {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_NETWORK_UPDATED,
                                event.newValue().value()));
                        notifyProviderUpdate(
                                event.oldValue().value(),
                                event.newValue().value());
                    });
                    break;
                case INSERT:
                    log.debug("Service network created {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_NETWORK_CREATED,
                                event.newValue().value()));
                        notifyProviderUpdate(null, event.newValue().value());
                    });
                    break;
                case REMOVE:
                    log.debug("Service network removed {}", event.oldValue());
                    eventExecutor.execute(() -> {
                        notifyProviderUpdate(event.oldValue().value(), null);
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_NETWORK_REMOVED,
                                event.oldValue().value()));
                    });
                    break;
                default:
                    log.error("Unsupported event type");
                    break;
            }
        }

        private void notifyProviderUpdate(ServiceNetwork oldValue, ServiceNetwork newValue) {
            Map<NetworkId, DependencyType> oldp =
                    oldValue != null ? oldValue.providers() : ImmutableMap.of();
            Map<NetworkId, DependencyType> newp =
                    newValue != null ? newValue.providers() : ImmutableMap.of();

            oldp.entrySet().stream().filter(p -> !newp.keySet().contains(p.getKey()))
                    .forEach(p -> {
                        Provider providerNet = Provider.builder()
                                .provider(serviceNetwork(p.getKey()))
                                .type(p.getValue())
                                .build();
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_NETWORK_PROVIDER_REMOVED,
                                oldValue,
                                providerNet
                        ));
            });

            newp.entrySet().stream().filter(p -> !oldp.keySet().contains(p.getKey()))
                    .forEach(p -> {
                        Provider providerNet = Provider.builder()
                                .provider(serviceNetwork(p.getKey()))
                                .type(p.getValue())
                                .build();
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_NETWORK_PROVIDER_ADDED,
                                newValue,
                                providerNet));
                    });
        }
    }

    private class ServicePortMapListener implements MapEventListener<PortId, ServicePort> {

        @Override
        public void event(MapEvent<PortId, ServicePort> event) {
            switch (event.type()) {
                case UPDATE:
                    log.debug("Service port updated {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_PORT_UPDATED,
                                serviceNetwork(event.newValue().value().networkId()),
                                event.newValue().value()));
                    });
                    break;
                case INSERT:
                    log.debug("Service port created {}", event.newValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_PORT_CREATED,
                                serviceNetwork(event.newValue().value().networkId()),
                                event.newValue().value()));
                    });
                    break;
                case REMOVE:
                    log.debug("Service port removed {}", event.oldValue());
                    eventExecutor.execute(() -> {
                        notifyDelegate(new ServiceNetworkEvent(
                                SERVICE_PORT_REMOVED,
                                serviceNetwork(event.oldValue().value().networkId()),
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
