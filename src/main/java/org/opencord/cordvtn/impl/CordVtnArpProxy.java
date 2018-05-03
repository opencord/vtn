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
package org.opencord.cordvtn.impl;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.ARP;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.opencord.cordvtn.api.Constants;
import org.opencord.cordvtn.api.CordVtnConfig;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.core.ServiceNetworkEvent;
import org.opencord.cordvtn.api.core.ServiceNetworkListener;
import org.opencord.cordvtn.api.core.ServiceNetworkService;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeService;
import org.opencord.cordvtn.api.node.CordVtnNodeState;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.opencord.cordvtn.api.Constants.DEFAULT_GATEWAY_MAC_STR;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Handles ARP requests for virtual network service IPs.
 */
@Component(immediate = true)
public class CordVtnArpProxy {
    protected final Logger log = getLogger(getClass());

    private static final String PRIVATE_GATEWAY_MAC = "privateGatewayMac";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService netConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService compConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeService nodeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceNetworkService snetService;

    @Property(name = PRIVATE_GATEWAY_MAC, value = "00:00:00:00:00:01",
            label = "Fake MAC address for virtual network gateway")
    private String privateGatewayMacStr = DEFAULT_GATEWAY_MAC_STR;
    private MacAddress privateGatewayMac = MacAddress.valueOf(privateGatewayMacStr);

    private final PacketProcessor packetProcessor = new InternalPacketProcessor();
    private final Map<IpAddress, MacAddress> gateways = Maps.newConcurrentMap();

    private NetworkConfigListener configListener = new InternalConfigListener();
    private ServiceNetworkListener snetListener = new InternalServiceNetworkListener();
    private ApplicationId appId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(Constants.CORDVTN_APP_ID);
        compConfigService.registerProperties(getClass());

        netConfigService.addListener(configListener);
        readPublicGateways();
        snetService.addListener(snetListener);
        readPrivateGateways();

        packetService.addProcessor(packetProcessor, PacketProcessor.director(0));
        requestPacket();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(packetProcessor);
        snetService.removeListener(snetListener);
        netConfigService.removeListener(configListener);
        compConfigService.unregisterProperties(getClass(), false);
        cancelPacket();

        log.info("Stopped");
    }

    @Modified
    protected void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();
        String updatedMac;

        updatedMac = Tools.get(properties, PRIVATE_GATEWAY_MAC);
        if (!Strings.isNullOrEmpty(updatedMac) &&
                !updatedMac.equals(privateGatewayMacStr)) {
            privateGatewayMacStr = updatedMac;
            privateGatewayMac = MacAddress.valueOf(privateGatewayMacStr);
        }

        log.info("Modified");
    }

    /**
     * Requests ARP packet.
     */
    private void requestPacket() {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.ARP.ethType().toShort())
                .build();

        packetService.requestPackets(
                selector,
                PacketPriority.CONTROL,
                appId,
                Optional.empty());
    }

    /**
     * Cancels ARP packet.
     */
    private void cancelPacket() {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.ARP.ethType().toShort())
                .build();

        packetService.cancelPackets(
                selector,
                PacketPriority.CONTROL,
                appId,
                Optional.empty());
    }

    private void readPrivateGateways() {
        snetService.serviceNetworks().stream()
                .filter(net -> net.type() == PRIVATE || net.type() == VSG)
                .filter(net -> net.serviceIp() != null)
                .forEach(net -> addGateway(net.serviceIp(), privateGatewayMac));
    }

    /**
     * Adds a given gateway IP and MAC address to this ARP proxy.
     *
     * @param gatewayIp gateway ip address
     * @param gatewayMac gateway mac address
     */
    private void addGateway(IpAddress gatewayIp, MacAddress gatewayMac) {
        checkNotNull(gatewayIp, "Gateway IP address cannot be null");
        checkArgument(gatewayMac != null && gatewayMac != MacAddress.NONE,
                      "Gateway MAC address cannot be null or NONE");

        MacAddress existing = gateways.get(gatewayIp);
        if (existing != null && !existing.equals(privateGatewayMac) &&
                gatewayMac.equals(privateGatewayMac)) {
            // this is public gateway IP and MAC configured via netcfg
            // don't update with private gateway MAC
            return;
        }
        gateways.put(gatewayIp, gatewayMac);
        log.debug("Added ARP proxy entry IP:{} MAC:{}", gatewayIp, gatewayMac);
    }

    /**
     * Removes a given service IP address from this ARP proxy.
     *
     * @param gatewayIp gateway ip address
     */
    private void removeGateway(IpAddress gatewayIp) {
        checkNotNull(gatewayIp);
        MacAddress existing = gateways.get(gatewayIp);
        if (existing == null) {
            return;
        }
        if (!existing.equals(privateGatewayMac)) {
            // this is public gateway IP and MAC configured via netcfg
            // do nothing
            return;
        }
        gateways.remove(gatewayIp);
        log.debug("Removed ARP proxy entry for IP:{} MAC: {}", gatewayIp, existing);
    }

    /**
     * Emits ARP reply with fake MAC address for a given ARP request.
     * It only handles requests for the registered gateway IPs and host IPs.
     *
     * @param context packet context
     * @param ethPacket ethernet packet
     */
    private void processArpRequest(PacketContext context, Ethernet ethPacket) {
        ARP arpPacket = (ARP) ethPacket.getPayload();
        Ip4Address targetIp = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());

        MacAddress gatewayMac = gateways.get(targetIp);
        MacAddress replyMac = gatewayMac != null ? gatewayMac :
                getMacFromHostService(targetIp);

        if (replyMac.equals(MacAddress.NONE)) {
            forwardArpRequest(context, ethPacket);
            return;
        }

        Ethernet ethReply = ARP.buildArpReply(
                targetIp,
                replyMac,
                ethPacket);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(context.inPacket().receivedFrom().port())
                .build();

        packetService.emit(new DefaultOutboundPacket(
                context.inPacket().receivedFrom().deviceId(),
                treatment,
                ByteBuffer.wrap(ethReply.serialize())));

        context.block();
    }

    private void processArpReply(PacketContext context, Ethernet ethPacket) {
        ARP arpPacket = (ARP) ethPacket.getPayload();
        Ip4Address targetIp = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());

        DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
        Host host = hostService.getHostsByIp(targetIp).stream()
                .filter(h -> h.location().deviceId().equals(deviceId))
                .findFirst()
                .orElse(null);

        if (host == null) {
            // do nothing for the unknown ARP reply
            log.trace("No host found for {} in {}", targetIp, deviceId);
            context.block();
            return;
        }

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(host.location().port())
                .build();

        packetService.emit(new DefaultOutboundPacket(
                deviceId,
                treatment,
                ByteBuffer.wrap(ethPacket.serialize())));

        context.block();
    }

    private void forwardArpRequest(PacketContext context, Ethernet ethPacket) {
        DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
        PortNumber inputPort = context.inPacket().receivedFrom().port();

        Host host = hostService.getHost(HostId.hostId(ethPacket.getSourceMAC()));
        if (host == null) {
            context.block();
            return;
        }

        NetworkType networkType = Instance.of(host).netType();
        if (networkType != MANAGEMENT_HOST && networkType != FLAT) {
            context.block();
            log.trace("Failed to handle ARP request");
            return;
        }

        PortNumber outputPort = networkType == MANAGEMENT_HOST ?
                hostMgmtPort(deviceId) : dataPort(deviceId);
        if (inputPort.equals(outputPort)) {
            context.block();
            return;
        }

        if (outputPort != null) {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(outputPort)
                    .build();

            packetService.emit(new DefaultOutboundPacket(
                    context.inPacket().receivedFrom().deviceId(),
                    treatment,
                    ByteBuffer.wrap(ethPacket.serialize())));
        } else {
            log.trace("Failed to handle ARP request");
        }

        context.block();
    }

    private PortNumber hostMgmtPort(DeviceId deviceId) {
        CordVtnNode node = nodeService.node(deviceId);
        if (node == null || node.state() != CordVtnNodeState.COMPLETE ||
                node.hostManagementInterface() == null) {
            return null;
        }
        Optional<Port> port = deviceService.getPorts(deviceId).stream()
                .filter(p -> p.annotations().value(PORT_NAME)
                        .equals(node.hostManagementInterface()) &&
                        p.isEnabled())
                .findAny();
        return port.map(Port::number).orElse(null);
    }

    private PortNumber dataPort(DeviceId deviceId) {
        CordVtnNode node = nodeService.node(deviceId);
        if (node == null || node.state() != CordVtnNodeState.COMPLETE ||
                node.dataInterface() == null) {
            return null;
        }
        Optional<Port> port = deviceService.getPorts(deviceId).stream()
                .filter(p -> p.annotations().value(PORT_NAME)
                        .equals(node.dataInterface()) &&
                        p.isEnabled())
                .findAny();
        return port.map(Port::number).orElse(null);
    }

    /**
     * Emits gratuitous ARP when a gateway mac address has been changed.
     *
     * @param gatewayIp gateway ip address to update MAC
     * @param instances set of instances to send gratuitous ARP packet
     */
    private void sendGratuitousArp(IpAddress gatewayIp, Set<Instance> instances) {
        MacAddress gatewayMac = gateways.get(gatewayIp);
        if (gatewayMac == null) {
            log.debug("Gateway {} is not registered to ARP proxy", gatewayIp);
            return;
        }

        Ethernet ethArp = buildGratuitousArp(gatewayIp.getIp4Address(), gatewayMac);
        instances.forEach(instance -> {
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(instance.portNumber())
                    .build();

            packetService.emit(new DefaultOutboundPacket(
                    instance.deviceId(),
                    treatment,
                    ByteBuffer.wrap(ethArp.serialize())));
        });
    }

    /**
     * Builds gratuitous ARP packet with a given IP and MAC address.
     *
     * @param ip ip address for TPA and SPA
     * @param mac new mac address
     * @return ethernet packet
     */
    private Ethernet buildGratuitousArp(IpAddress ip, MacAddress mac) {
        Ethernet eth = new Ethernet();

        eth.setEtherType(Ethernet.TYPE_ARP);
        eth.setSourceMACAddress(mac);
        eth.setDestinationMACAddress(MacAddress.BROADCAST);

        ARP arp = new ARP();
        arp.setOpCode(ARP.OP_REQUEST);
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        arp.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
        arp.setProtocolType(ARP.PROTO_TYPE_IP);
        arp.setProtocolAddressLength((byte) Ip4Address.BYTE_LENGTH);

        arp.setSenderHardwareAddress(mac.toBytes());
        arp.setTargetHardwareAddress(MacAddress.BROADCAST.toBytes());
        arp.setSenderProtocolAddress(ip.getIp4Address().toOctets());
        arp.setTargetProtocolAddress(ip.getIp4Address().toOctets());

        eth.setPayload(arp);
        return eth;
    }

    /**
     * Returns MAC address of a host with a given target IP address by asking to
     * host service. It does not support overlapping IP.
     *
     * @param targetIp target ip
     * @return mac address, or NONE mac address if it fails to find the mac
     */
    private MacAddress getMacFromHostService(IpAddress targetIp) {
        checkNotNull(targetIp);

        Host host = hostService.getHostsByIp(targetIp)
                .stream()
                .findFirst()
                .orElse(null);

        if (host != null) {
            log.trace("Found MAC from host service for {}", targetIp);
            return host.mac();
        } else {
            return MacAddress.NONE;
        }
    }

    private class InternalPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            Ethernet ethPacket = context.inPacket().parsed();
            if (ethPacket == null || ethPacket.getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }

            ARP arpPacket = (ARP) ethPacket.getPayload();
            switch (arpPacket.getOpCode()) {
                case ARP.OP_REQUEST:
                    processArpRequest(context, ethPacket);
                    break;
                case ARP.OP_REPLY:
                    processArpReply(context, ethPacket);
                    break;
                default:
                    break;
            }
        }
    }

    private class InternalServiceNetworkListener implements ServiceNetworkListener {

        @Override
        public boolean isRelevant(ServiceNetworkEvent event) {
            ServiceNetwork snet = event.subject();
            return snet.serviceIp() != null;
        }

        @Override
        public void event(ServiceNetworkEvent event) {
            ServiceNetwork snet = event.subject();
            switch (event.type()) {
                case SERVICE_NETWORK_CREATED:
                    if (snet.type() == PRIVATE || snet.type() == VSG) {
                        addGateway(snet.serviceIp(), privateGatewayMac);
                    }
                    break;
                case SERVICE_NETWORK_UPDATED:
                    if (snet.type() == PRIVATE || snet.type() == VSG) {
                        addGateway(snet.serviceIp(), privateGatewayMac);
                    } else {
                        // don't service ARP for the other network gateway
                        removeGateway(snet.serviceIp());
                    }
                    break;
                case SERVICE_NETWORK_REMOVED:
                    removeGateway(snet.serviceIp());
                    break;
                case SERVICE_PORT_CREATED:
                case SERVICE_PORT_UPDATED:
                case SERVICE_PORT_REMOVED:
                default:
                    // do nothing for the other events
                    break;
            }
        }
    }

    private void readPublicGateways() {
        CordVtnConfig config = netConfigService.getConfig(appId, CordVtnConfig.class);
        if (config == null) {
            return;
        }

        config.publicGateways().forEach(this::addGateway);
        // TODO send gratuitous arp in case the MAC is changed
    }

    private class InternalConfigListener implements NetworkConfigListener {

        @Override
        public boolean isRelevant(NetworkConfigEvent event) {
            return event.configClass().equals(CordVtnConfig.class);
        }

        @Override
        public void event(NetworkConfigEvent event) {

            switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                    readPublicGateways();
                    break;
                default:
                    break;
            }
        }
    }
}
