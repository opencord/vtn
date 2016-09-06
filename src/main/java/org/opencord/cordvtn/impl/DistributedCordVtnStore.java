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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
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
import org.opencord.cordvtn.api.NetworkId;
import org.opencord.cordvtn.api.PortId;
import org.opencord.cordvtn.api.ServiceNetwork;
import org.opencord.cordvtn.api.ServiceNetwork.DirectAccessType;
import org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType;
import org.opencord.cordvtn.api.ServicePort;
import org.slf4j.Logger;

import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static org.opencord.cordvtn.api.Constants.CORDVTN_APP_ID;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of the cordvtn service.
 */
@Component(immediate = true)
@Service
public class DistributedCordVtnStore implements CordVtnStore {
    protected final Logger log = getLogger(getClass());

    private static final String MSG_SERVICE_NET  = "Service network %s %s";
    private static final String MSG_SERVICE_PORT = "Service port %s %s";
    private static final String ERR_SYNC = "VTN store is out of sync: ";
    private static final String ERR_NOT_FOUND = " does not exist";
    private static final String ERR_DUPLICATE = " already exists with different properties";
    private static final String CREATED = "created";
    private static final String UPDATED = "updated";
    private static final String REMOVED = "removed";

    private static final KryoNamespace.Builder SERIALIZER = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(ServiceNetwork.class)
            .register(NetworkId.class)
            .register(ServiceNetworkType.class)
            .register(DirectAccessType.class)
            .register(ServicePort.class)
            .register(PortId.class)
            .register(AddressPair.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    private ConsistentMap<NetworkId, ServiceNetwork> serviceNetwokStore;
    private ConsistentMap<PortId, ServicePort> servicePortStore;

    @Activate
    protected void activate() {
        ApplicationId appId = coreService.getAppId(CORDVTN_APP_ID);

        serviceNetwokStore = storageService.<NetworkId, ServiceNetwork>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER.build()))
                .withName("cordvtn-servicenetstore")
                .withApplicationId(appId)
                .build();

        servicePortStore = storageService.<PortId, ServicePort>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER.build()))
                .withName("cordvtn-serviceportstore")
                .withApplicationId(appId)
                .build();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void createServiceNetwork(ServiceNetwork serviceNet) {
        synchronized (this) {
            serviceNetwokStore.compute(serviceNet.id(), (id, snet) -> {
                final String error = ERR_SYNC + serviceNet.id().id() + ERR_DUPLICATE;
                checkArgument(snet == null || snet.equals(serviceNet), error);
                return serviceNet;
            });
            log.info(String.format(MSG_SERVICE_NET, CREATED, serviceNet));
        }
    }

    @Override
    public void updateServiceNetwork(ServiceNetwork serviceNet) {
        synchronized (this) {
            serviceNetwokStore.compute(serviceNet.id(), (id, snet) -> {
                final String error = ERR_SYNC + serviceNet.id().id() + ERR_NOT_FOUND;
                checkArgument(snet != null, ERR_SYNC + error);
                return serviceNet;
            });
            log.info(String.format(MSG_SERVICE_NET, UPDATED, serviceNet));
        }
    }

    @Override
    public ServiceNetwork getServiceNetwork(NetworkId netId) {
        Versioned<ServiceNetwork> versioned = serviceNetwokStore.get(netId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<ServiceNetwork> getServiceNetworks() {
        return serviceNetwokStore.values()
                .stream().map(Versioned::value).collect(Collectors.toSet());
    }

    @Override
    public void removeServiceNetwork(NetworkId netId) {
        synchronized (this) {
            final String error = ERR_SYNC + netId.id() + ERR_NOT_FOUND;
            checkArgument(serviceNetwokStore.remove(netId) != null, error);
            log.info(String.format(MSG_SERVICE_NET, REMOVED, netId));
        }
    }

    @Override
    public void createServicePort(ServicePort servicePort) {
        synchronized (this) {
            servicePortStore.compute(servicePort.id(), (id, sport) -> {
                final String error = ERR_SYNC + servicePort.id().id() + ERR_DUPLICATE;
                checkArgument(sport == null || sport.equals(servicePort), error);
                return servicePort;
            });
            log.info(String.format(MSG_SERVICE_PORT, CREATED, servicePort));
        }
    }

    @Override
    public ServicePort getServicePort(PortId portId) {
        Versioned<ServicePort> versioned = servicePortStore.get(portId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<ServicePort> getServicePorts() {
        return servicePortStore.values()
                .stream().map(Versioned::value).collect(Collectors.toSet());
    }

    @Override
    public void removeServicePort(PortId portId) {
        synchronized (this) {
            final String error = ERR_SYNC + portId.id() + ERR_NOT_FOUND;
            checkArgument(servicePortStore.remove(portId) != null, error);
            log.info(String.format(MSG_SERVICE_PORT, REMOVED, portId));
        }
    }
}
