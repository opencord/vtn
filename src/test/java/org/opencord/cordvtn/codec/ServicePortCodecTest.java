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
package org.opencord.cordvtn.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.net.AddressPair;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.impl.DefaultServicePort;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.opencord.cordvtn.codec.ServicePortJsonMatcher.matchesServicePort;

/**
 * Unit tests for ServiceNetwork codec.
 */
public final class ServicePortCodecTest {

    private static final String SERVICE_PORT = "servicePort";
    private static final String ID = "id";
    private static final String NETWORK_ID = "network_id";
    private static final String NAME = "name";
    private static final String IP_ADDRESS = "ip_address";
    private static final String MAC_ADDRESS = "mac_address";
    private static final String FLOATING_ADDRESS_PAIRS = "floating_address_pairs";
    private static final String VLAN_ID = "vlan_id";

    private static final PortId PORT_ID_1 = PortId.of("port-1");
    private static final NetworkId NETWORK_ID_1 = NetworkId.of("network-1");
    private static final String PORT_NAME_1 = "tap1";
    private static final MacAddress MAC_ADDRESS_1 = MacAddress.valueOf("00:00:00:00:00:01");
    private static final IpAddress IP_ADDRESS_1 = IpAddress.valueOf("10.0.0.1");
    private static final VlanId VLAN_ID_1 = VlanId.vlanId("222");

    private static final AddressPair ADDRESS_PAIR_1 = AddressPair.of(
            IpAddress.valueOf("192.168.0.1"),
            MacAddress.valueOf("02:42:0a:06:01:01"));
    private static final AddressPair ADDRESS_PAIR_2 = AddressPair.of(
            IpAddress.valueOf("192.168.0.2"),
            MacAddress.valueOf("02:42:0a:06:01:02"));

    private static final Set<AddressPair> ADDRESS_PAIRS_1 =
            new HashSet<AddressPair>() {
                {
                    add(ADDRESS_PAIR_1);
                    add(ADDRESS_PAIR_2);
                }
            };

    private static final ServicePort PORT_1 = DefaultServicePort.builder()
            .id(PORT_ID_1)
            .networkId(NETWORK_ID_1)
            .name(PORT_NAME_1)
            .ip(IP_ADDRESS_1)
            .mac(MAC_ADDRESS_1)
            .addressPairs(ADDRESS_PAIRS_1)
            .vlanId(VLAN_ID_1)
            .build();

    private JsonCodec<ServicePort> codec;
    private MockCodecContext context;

    /**
     * Creates a context and gets the servicePort codec for each test.
     */
    @Before
    public void setUp() {
        context = new MockCodecContext();
        codec = context.codec(ServicePort.class);
        assertThat(codec, notNullValue());
    }

    /**
     * Checks if encoding service port works properly.
     */
    @Test
    public void testServicePortEncode() {
        ObjectNode jsonPort = codec.encode(PORT_1, context);
        assertThat(jsonPort, notNullValue());
        assertThat(jsonPort, matchesServicePort(PORT_1));
    }

    /**
     * Checks if decoding service port works properly.
     */
    @Test
    public void testServicePortDecode() throws IOException {
        ServicePort sPort = getServicePort("service-port.json");
        assertThat(sPort.id(), is(PORT_ID_1));
        assertThat(sPort.networkId(), is(NETWORK_ID_1));
        assertThat(sPort.name(), is(PORT_NAME_1));
        assertThat(sPort.ip(), is(IP_ADDRESS_1));
        assertThat(sPort.mac(), is(MAC_ADDRESS_1));
        assertThat(sPort.addressPairs(), is(ADDRESS_PAIRS_1));
        assertThat(sPort.vlanId(), is(VLAN_ID_1));
    }

    /**
     * Checks if decoding service port without ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeMissingId() {
        final JsonNode jsonInvalidId = context.mapper().createObjectNode()
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort());
        codec.decode((ObjectNode) jsonInvalidId, context);
    }

    /**
     * Checks if decoding service port with empty ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeEmptyId() {
        final JsonNode jsonInvalidId = context.mapper().createObjectNode()
                .put(ID, "")
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort());
        codec.decode((ObjectNode) jsonInvalidId, context);
    }

    /**
     * Checks if decoding service port with null ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullId() {
        final JsonNode jsonInvalidId = context.mapper().createObjectNode()
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(ID, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidId, context);
    }

    /**
     * Checks if empty string is not allowed for network ID.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeEmptyNetworkId() {
        final JsonNode jsonInvalidId = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, "")
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort());
        codec.decode((ObjectNode) jsonInvalidId, context);
    }

    /**
     * Checks if null is not allowed for network ID.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullNetworkId() {
        final JsonNode jsonInvalidId = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(NETWORK_ID, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidId, context);
    }

    /**
     * Checks if empty string is not allowed for port name.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeEmptyName() {
        JsonNode jsonInvalidName = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, "")
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort());
        codec.decode((ObjectNode) jsonInvalidName, context);
    }

    /**
     * Checks if null is not allowed for port name.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullName() {
        final JsonNode jsonInvalidName = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(NAME, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidName, context);
    }

    /**
     * Checks if invalid IP address string is not allowed.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeInvalidIpAddress() {
        final JsonNode jsonInvalidIp = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, "ipAddress")
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort());
        codec.decode((ObjectNode) jsonInvalidIp, context);
    }

    /**
     * Checks if invalid IP address string is not allowed.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullIpAddress() {
        final JsonNode jsonInvalidIp = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(IP_ADDRESS, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidIp, context);
    }

    /**
     * Checks if invalid MAC address string is not allowed.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeInvalidMacAddress() {
        final JsonNode jsonInvalidMac = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, "macAddress")
                .put(VLAN_ID, VLAN_ID_1.toShort());
        codec.decode((ObjectNode) jsonInvalidMac, context);
    }

    /**
     * Checks if null is not allowed for MAC address.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullMacAddress() {
        final JsonNode jsonInvalidMac = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(MAC_ADDRESS, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidMac, context);
    }

    /**
     * Checks if invalid VLAN ID is not allowed.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeInvalidVlanId() {
        final JsonNode jsonInvalidVlan = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, "vlanId");
        codec.decode((ObjectNode) jsonInvalidVlan, context);
    }

    /**
     * Checks if null is not allowed for VLAN ID.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullVlanId() {
        final JsonNode jsonInvalidVlan = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .set(VLAN_ID, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidVlan, context);
    }

    /**
     * Checks if only array node is allowed for floating address pairs.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNonArrayAddressPairs() {
        final JsonNode jsonAddrPair = context.mapper().createObjectNode()
                .put(IP_ADDRESS, ADDRESS_PAIR_1.ip().toString())
                .put(MAC_ADDRESS, ADDRESS_PAIR_1.mac().toString());
        final JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, jsonAddrPair);
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    /**
     * Checks if null and empty array node is allowed for floating address pairs.
     */
    @Test
    public void testServicePortDecodeNullAndEmptyAddressPairs() {
        JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);

        jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, context.mapper().createArrayNode());
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    /**
     * Checks if floating address pair without IP address field fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeMissingFloatingAddrPairIp() {
        final ArrayNode jsonAddrPairs = context.mapper().createArrayNode();
        final JsonNode jsonAddrPair = context.mapper().createObjectNode()
                .put(MAC_ADDRESS, ADDRESS_PAIR_1.mac().toString());
        jsonAddrPairs.add(jsonAddrPair);
        final JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, jsonAddrPairs);
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    /**
     * Checks if floating address pair without MAC address field fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeMissingFloatingAddrPairMac() {
        final ArrayNode jsonAddrPairs = context.mapper().createArrayNode();
        final JsonNode jsonAddrPair = context.mapper().createObjectNode()
                .put(IP_ADDRESS, ADDRESS_PAIR_1.ip().toString());
        jsonAddrPairs.add(jsonAddrPair);
        final JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, jsonAddrPairs);
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    /**
     * Checks if null IP address for floating address pair fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullFloatingAddrPairIp() {
        final ArrayNode jsonAddrPairs = context.mapper().createArrayNode();
        final JsonNode jsonAddrPair = context.mapper().createObjectNode()
                .put(MAC_ADDRESS, ADDRESS_PAIR_1.mac().toString())
                .set(IP_ADDRESS, NullNode.getInstance());
        jsonAddrPairs.add(jsonAddrPair);
        final JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, jsonAddrPairs);
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    /**
     * Checks if null MAC address for floating address pair fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeNullFloatingAddrPairMac() {
        final ArrayNode jsonAddrPairs = context.mapper().createArrayNode();
        final JsonNode jsonAddrPair = context.mapper().createObjectNode()
                .put(IP_ADDRESS, ADDRESS_PAIR_1.ip().toString())
                .set(MAC_ADDRESS, NullNode.getInstance());
        jsonAddrPairs.add(jsonAddrPair);
        final JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, jsonAddrPairs);
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    /**
     * Checks if invalid IP address for floating address pair fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeInvalidFloatingAddrPairIp() {
        final ArrayNode jsonAddrPairs = context.mapper().createArrayNode();
        final JsonNode jsonAddrPair = context.mapper().createObjectNode()
                .put(IP_ADDRESS, "ipAddress")
                .put(MAC_ADDRESS, ADDRESS_PAIR_1.mac().toString());
        jsonAddrPairs.add(jsonAddrPair);
        final JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, jsonAddrPairs);
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    /**
     * Checks if invalid MAC address for floating address pair fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServicePortDecodeInvalidFloatingAddrPairMac() {
        final ArrayNode jsonAddrPairs = context.mapper().createArrayNode();
        final JsonNode jsonAddrPair = context.mapper().createObjectNode()
                .put(IP_ADDRESS, ADDRESS_PAIR_1.ip().toString())
                .put(MAC_ADDRESS, "macAddress");
        jsonAddrPairs.add(jsonAddrPair);
        final JsonNode jsonInvalidAddrPair = context.mapper().createObjectNode()
                .put(ID, PORT_ID_1.id())
                .put(NETWORK_ID, NETWORK_ID_1.id())
                .put(NAME, PORT_NAME_1)
                .put(IP_ADDRESS, IP_ADDRESS_1.toString())
                .put(MAC_ADDRESS, MAC_ADDRESS_1.toString())
                .put(VLAN_ID, VLAN_ID_1.toShort())
                .set(FLOATING_ADDRESS_PAIRS, jsonAddrPairs);
        codec.decode((ObjectNode) jsonInvalidAddrPair, context);
    }

    private ServicePort getServicePort(String resource) throws IOException {
        InputStream jsonStream = ServicePortCodecTest.class.getResourceAsStream(resource);
        JsonNode jsonNode = context.mapper().readTree(jsonStream).get(SERVICE_PORT);
        assertThat(jsonNode, notNullValue());

        ServicePort sPort = codec.decode((ObjectNode) jsonNode, context);
        assertThat(sPort, notNullValue());
        return sPort;
    }
}
