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
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.ARP;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.opencord.cordvtn.api.CordVtnConfig;
import org.opencord.cordvtn.api.core.Instance;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.impl.handler.AbstractInstanceHandler;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Handles ARP requests for virtual network service IPs.
 */
@Component(immediate = true)
public class CordVtnArpProxy extends AbstractInstanceHandler {
    protected final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordVtnNodeManager nodeManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    private final PacketProcessor packetProcessor = new InternalPacketProcessor();
    private final Map<Ip4Address, MacAddress> gateways = Maps.newConcurrentMap();

    private MacAddress privateGatewayMac = MacAddress.NONE;
    private NetworkConfigListener configListener = new InternalConfigListener();

    @Activate
    protected void activate() {
        super.activate();
        configService.addListener(configListener);
        readConfiguration();

        packetService.addProcessor(packetProcessor, PacketProcessor.director(0));
        requestPacket();
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(packetProcessor);
        configService.removeListener(configListener);
        super.deactivate();
    }

    /**
     * Requests ARP packet.
     */
    private void requestPacket() {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.ARP.ethType().toShort())
                .build();

        packetService.requestPackets(selector,
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

        packetService.cancelPackets(selector,
                                    PacketPriority.CONTROL,
                                    appId,
                                    Optional.empty());
    }

    /**
     * Adds a given gateway IP and MAC address to this ARP proxy.
     *
     * @param gatewayIp gateway ip address
     * @param gatewayMac gateway mac address
     */
    private void addGateway(IpAddress gatewayIp, MacAddress gatewayMac) {
        checkNotNull(gatewayIp);
        checkNotNull(gatewayMac);
        gateways.put(gatewayIp.getIp4Address(), gatewayMac);
    }

    /**
     * Removes a given service IP address from this ARP proxy.
     *
     * @param gatewayIp gateway ip address
     */
    private void removeGateway(IpAddress gatewayIp) {
        checkNotNull(gatewayIp);
        gateways.remove(gatewayIp.getIp4Address());
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
            log.trace("Failed to find MAC for {}", targetIp);
            forwardManagementArpRequest(context, ethPacket);
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

    private void forwardManagementArpRequest(PacketContext context, Ethernet ethPacket) {
        DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
        PortNumber hostMgmtPort = nodeManager.hostManagementPort(deviceId);
        Host host = hostService.getConnectedHosts(context.inPacket().receivedFrom())
                .stream()
                .findFirst().orElse(null);

        if (host == null ||
                !Instance.of(host).netType().name().contains("MANAGEMENT") ||
                hostMgmtPort == null) {
            context.block();
            return;
        }

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(hostMgmtPort)
                .build();

        packetService.emit(new DefaultOutboundPacket(
                context.inPacket().receivedFrom().deviceId(),
                treatment,
                ByteBuffer.wrap(ethPacket.serialize())));

        log.trace("Forward ARP request to management network");
        context.block();
    }

    /**
     * Emits gratuitous ARP when a gateway mac address has been changed.
     *
     * @param gatewayIp gateway ip address to update MAC
     * @param instances set of instances to send gratuitous ARP packet
     */
    private void sendGratuitousArp(IpAddress gatewayIp, Set<Instance> instances) {
        MacAddress gatewayMac = gateways.get(gatewayIp.getIp4Address());
        if (gatewayMac == null) {
            log.debug("Gateway {} is not registered to ARP proxy", gatewayIp);
            return;
        }

        Ethernet ethArp = buildGratuitousArp(gatewayIp.getIp4Address(), gatewayMac);
        instances.stream().forEach(instance -> {
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

    @Override
    public void instanceDetected(Instance instance) {
        // TODO remove this when XOS provides access agent information
        // and handle it the same way wit the other instances
        if (instance.netType() == ACCESS_AGENT) {
            return;
        }

        ServiceNetwork snet = getServiceNetwork(instance);
        if (snet.type() != PUBLIC && snet.type() != MANAGEMENT_HOST &&
                snet.type() != MANAGEMENT_LOCAL) {
            log.trace("Added IP:{} MAC:{}", snet.serviceIp(), privateGatewayMac);
            addGateway(snet.serviceIp(), privateGatewayMac);
            // send gratuitous ARP for the existing VMs when ONOS is restarted
            sendGratuitousArp(snet.serviceIp(), ImmutableSet.of(instance));
        }
    }

    @Override
    public void instanceRemoved(Instance instance) {
        // TODO remove this when XOS provides access agent information
        // and handle it the same way wit the other instances
        if (instance.netType() == ACCESS_AGENT) {
            return;
        }

        ServiceNetwork vtnNet = getServiceNetwork(instance);
        // remove this network gateway from proxy ARP if no instance presents
        if (vtnNet.type() == PRIVATE &&
                getInstances(vtnNet.id()).isEmpty()) {
            removeGateway(vtnNet.serviceIp());
        }
    }

    private void readConfiguration() {
        CordVtnConfig config = configService.getConfig(appId, CordVtnConfig.class);
        if (config == null) {
            log.debug("No configuration found");
            return;
        }

        privateGatewayMac = config.privateGatewayMac();
        log.debug("Added private gateway MAC {}", privateGatewayMac);

        config.publicGateways().entrySet().stream().forEach(entry -> {
            addGateway(entry.getKey(), entry.getValue());
            log.debug("Added public gateway IP {}, MAC {}",
                      entry.getKey(), entry.getValue());
        });
        // TODO send gratuitous arp in case the MAC is changed
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
