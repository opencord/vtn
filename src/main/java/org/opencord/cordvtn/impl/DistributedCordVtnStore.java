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
import org.opencord.cordvtn.api.SubnetId;
import org.opencord.cordvtn.api.VtnNetwork;
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

import java.util.Objects;
import java.util.Optional;
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
    private static final String MSG_NET = "Network %s %s";
    private static final String MSG_PORT = "Port %s %s";
    private static final String MSG_SUBNET = "Subnet %s %s";

    private static final String ERR_SYNC = "VTN store is out of sync: ";
    private static final String ERR_NOT_FOUND = " does not exist";
    private static final String ERR_DUPLICATE = " already exists with different properties";

    private static final String CREATED = "created";
    private static final String UPDATED = "updated";
    private static final String REMOVED = "removed";

    private static final KryoNamespace SERIALIZER_SERVICE = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(ServiceNetwork.class)
            .register(NetworkId.class)
            .register(ServiceNetworkType.class)
            .register(DirectAccessType.class)
            .register(ServicePort.class)
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

    private ConsistentMap<NetworkId, ServiceNetwork> serviceNetworkStore;
    private ConsistentMap<NetworkId, Network> networkStore;
    private ConsistentMap<PortId, ServicePort> servicePortStore;
    private ConsistentMap<PortId, Port> portStore;
    private ConsistentMap<SubnetId, Subnet> subnetStore;

    @Activate
    protected void activate() {
        ApplicationId appId = coreService.getAppId(CORDVTN_APP_ID);

        serviceNetworkStore = storageService.<NetworkId, ServiceNetwork>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_SERVICE))
                .withName("cordvtn-servicenetstore")
                .withApplicationId(appId)
                .build();

        servicePortStore = storageService.<PortId, ServicePort>consistentMapBuilder()
                .withSerializer(Serializer.using(SERIALIZER_SERVICE))
                .withName("cordvtn-serviceportstore")
                .withApplicationId(appId)
                .build();

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
        log.info("Stopped");
    }

    @Override
    public void createServiceNetwork(ServiceNetwork serviceNet) {
        synchronized (this) {
            serviceNetworkStore.compute(serviceNet.id(), (id, existing) -> {
                final String error = ERR_SYNC + serviceNet.id().id() + ERR_DUPLICATE;
                checkArgument(existing == null || existing.equals(serviceNet), error);
                return serviceNet;
            });
            log.info(String.format(MSG_SERVICE_NET, CREATED, serviceNet));
        }
    }

    @Override
    public void updateServiceNetwork(ServiceNetwork serviceNet) {
        synchronized (this) {
            serviceNetworkStore.compute(serviceNet.id(), (id, existing) -> {
                final String error = ERR_SYNC + serviceNet.id().id() + ERR_NOT_FOUND;
                checkArgument(existing != null, ERR_SYNC + error);
                return serviceNet;
            });
            log.info(String.format(MSG_SERVICE_NET, UPDATED, serviceNet));
        }
    }

    @Override
    public ServiceNetwork getServiceNetwork(NetworkId netId) {
        Versioned<ServiceNetwork> versioned = serviceNetworkStore.get(netId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<ServiceNetwork> getServiceNetworks() {
        return serviceNetworkStore.values()
                .stream().map(Versioned::value).collect(Collectors.toSet());
    }

    @Override
    public void removeServiceNetwork(NetworkId netId) {
        synchronized (this) {
            final String error = ERR_SYNC + netId.id() + ERR_NOT_FOUND;
            checkArgument(serviceNetworkStore.remove(netId) != null, error);
            log.info(String.format(MSG_SERVICE_NET, REMOVED, netId));
        }
    }

    @Override
    public void createServicePort(ServicePort servicePort) {
        synchronized (this) {
            servicePortStore.compute(servicePort.id(), (id, existing) -> {
                final String error = ERR_SYNC + servicePort.id().id() + ERR_DUPLICATE;
                checkArgument(existing == null || existing.equals(servicePort), error);
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

    @Override
    public void createNetwork(Network net) {
        synchronized (this) {
            networkStore.compute(NetworkId.of(net.getId()), (id, existing) -> {
                final String error = ERR_SYNC + net.getId() + ERR_DUPLICATE;
                checkArgument(existing == null || net.equals(existing), error);
                return net;
            });
            log.info(String.format(MSG_NET, CREATED, net));
        }
    }

    @Override
    public void updateNetwork(Network net) {
        synchronized (this) {
            networkStore.compute(NetworkId.of(net.getId()), (id, existing) -> {
                final String error = ERR_SYNC + net.getId() + ERR_NOT_FOUND;
                checkArgument(existing != null, ERR_SYNC + error);
                return net;
            });
            log.info(String.format(MSG_NET, UPDATED, net));
        }
    }

    @Override
    public Network getNetwork(NetworkId netId) {
        Versioned<Network> versioned = networkStore.get(netId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<Network> getNetworks() {
        return networkStore.values().stream()
                .map(Versioned::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void removeNetwork(NetworkId netId) {
        synchronized (this) {
            final String error = ERR_SYNC + netId.id() + ERR_NOT_FOUND;
            checkArgument(networkStore.remove(netId) != null, error);
            log.info(String.format(MSG_NET, REMOVED, netId));
        }
    }

    @Override
    public void createPort(Port port) {
        synchronized (this) {
            portStore.compute(PortId.of(port.getId()), (id, existing) -> {
                final String error = ERR_SYNC + port.getId() + ERR_DUPLICATE;
                checkArgument(existing == null || port.equals(existing), error);
                return port;
            });
            log.info(String.format(MSG_PORT, CREATED, port));
        }
    }

    @Override
    public void updatePort(Port port) {
        synchronized (this) {
            portStore.compute(PortId.of(port.getId()), (id, existing) -> {
                final String error = ERR_SYNC + port.getId() + ERR_NOT_FOUND;
                checkArgument(existing != null, ERR_SYNC + error);
                return port;
            });
            log.info(String.format(MSG_PORT, UPDATED, port));
        }
    }

    @Override
    public Port getPort(PortId portId) {
        Versioned<Port> versioned = portStore.get(portId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Set<Port> getPorts() {
        return portStore.values().stream()
                .map(Versioned::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void removePort(PortId portId) {
        synchronized (this) {
            final String error = ERR_SYNC + portId.id() + ERR_NOT_FOUND;
            checkArgument(portStore.remove(portId) != null, error);
            log.info(String.format(MSG_PORT, REMOVED, portId));
        }
    }

    @Override
    public void createSubnet(Subnet subnet) {
        synchronized (this) {
            subnetStore.compute(SubnetId.of(subnet.getId()), (id, existing) -> {
                final String error = ERR_SYNC + subnet.getId() + ERR_DUPLICATE;
                checkArgument(existing == null || subnet.equals(existing), error);
                return subnet;
            });
            log.info(String.format(MSG_SUBNET, CREATED, subnet));
        }
    }

    @Override
    public void updateSubnet(Subnet subnet) {
        synchronized (this) {
            subnetStore.compute(SubnetId.of(subnet.getId()), (id, existing) -> {
                final String error = ERR_SYNC + subnet.getId() + ERR_NOT_FOUND;
                checkArgument(existing != null, ERR_SYNC + error);
                return subnet;
            });
            log.info(String.format(MSG_SUBNET, UPDATED, subnet));
        }
    }

    @Override
    public Subnet getSubnet(SubnetId subnetId) {
        Versioned<Subnet> versioned = subnetStore.get(subnetId);
        return versioned == null ? null : versioned.value();
    }

    @Override
    public Subnet getSubnet(NetworkId netId) {
        // TODO fix networking-onos to send Network UPDATE when subnet created
        // currently, network subnets list is always empty
        Optional<Subnet> subnet = getSubnets().stream()
                .filter(s -> Objects.equals(s.getNetworkId(), netId.id()))
                .findFirst();
        return subnet.orElse(null);
    }

    @Override
    public Set<Subnet> getSubnets() {
        return subnetStore.values().stream()
                .map(Versioned::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void removeSubnet(SubnetId subnetId) {
        synchronized (this) {
            final String error = ERR_SYNC + subnetId.id() + ERR_NOT_FOUND;
            checkArgument(subnetStore.remove(subnetId) != null, error);
            log.info(String.format(MSG_SUBNET, REMOVED, subnetId));
        }
    }

    @Override
    public VtnNetwork getVtnNetwork(NetworkId netId) {
        Network net = getNetwork(netId);
        Subnet subnet = getSubnet(netId);
        if (net == null || subnet == null) {
            log.debug("net:" + net + "subnet:" + subnet + ERR_NOT_FOUND);
            return null;
        }
        return VtnNetwork.of(net, subnet, getServiceNetwork(netId));
    }

    @Override
    public VtnPort getVtnPort(PortId portId) {
        Port port = getPort(portId);
        if (port == null) {
            log.debug("Port " + portId + ERR_NOT_FOUND);
            return null;
        }
        return VtnPort.of(port, getServicePort(portId));
    }
}
