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
package org.opencord.cordvtn.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.SegmentId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType;
import org.opencord.cordvtn.impl.DefaultServiceNetwork;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType.BIDIRECTIONAL;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.PRIVATE;
import static org.opencord.cordvtn.codec.ServiceNetworkJsonMatcher.matchesServiceNetwork;

/**
 * Unit tests for ServiceNetwork codec.
 */
public final class ServiceNetworkCodecTest {
    private static final String SERVICE_NETWORK = "serviceNetwork";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String PROVIDERS = "providers";
    private static final String SEGMENT_ID = "segment_id";
    private static final String SUBNET = "subnet";
    private static final String SERVICE_IP = "service_ip";
    private static final String DEP_TYPE = "bidirectional";

    private static final String NAME_1 = "network_1";
    private static final NetworkId ID_1 = NetworkId.of("network_1");
    private static final NetworkId PROVIDER_ID_1 = NetworkId.of("provider_1");
    private static final SegmentId SEGMENT_ID_1 = SegmentId.of(1L);
    private static final IpPrefix SUBNET_1 = IpPrefix.valueOf("192.168.0.0/24");
    private static final IpAddress SERVICE_IP_1 = IpAddress.valueOf("192.168.0.1");

    private static final Map<NetworkId, DependencyType> PROVIDER_1 =
            new HashMap<NetworkId, DependencyType>() {
                {
                    put(NetworkId.of("provider_1"), BIDIRECTIONAL);
                }
            };

    private static final ServiceNetwork NETWORK_1 = DefaultServiceNetwork.builder()
            .id(ID_1)
            .name(NAME_1)
            .type(PRIVATE)
            .segmentId(SEGMENT_ID_1)
            .subnet(SUBNET_1)
            .serviceIp(SERVICE_IP_1)
            .providers(PROVIDER_1)
            .build();

    private JsonCodec<ServiceNetwork> codec;
    private MockCodecContext context;

    /**
     * Sets up for each test.
     * Creates a context and fetches the ServiceNetwork codec.
     */
    @Before
    public void setUp() {
        context = new MockCodecContext();
        codec = context.codec(ServiceNetwork.class);
        assertThat(codec, notNullValue());
    }

    /**
     * Checks if encoding service network works properly.
     */
    @Test
    public void testServiceNetworkEncode() {
        ObjectNode networkJson = codec.encode(NETWORK_1, context);
        assertThat(networkJson, notNullValue());
        assertThat(networkJson, matchesServiceNetwork(NETWORK_1));
    }

    /**
     * Checks if decoding service network works properly.
     */
    @Test
    public void testServiceNetworkDecode() throws IOException {
        ServiceNetwork sNet = getServiceNetwork("service-network.json");
        assertThat(sNet.id(), is(ID_1));
        assertThat(sNet.name(), is(NAME_1));
        assertThat(sNet.type(), is(PRIVATE));
        assertThat(sNet.providers(), is(PROVIDER_1));
    }

    /**
     * Checks if decoding service network without ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeMissingId() {
        final JsonNode jsonMissingId = context.mapper().createObjectNode()
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString());
        codec.decode((ObjectNode) jsonMissingId, context);
    }

    /**
     * Checks if decoding service network with empty ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeEmptyId() {
        final JsonNode jsonEmptyId = context.mapper().createObjectNode()
                .put(ID, "")
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString());
        codec.decode((ObjectNode) jsonEmptyId, context);
    }

    /**
     * Checks if decoding service network with null ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeNullId() {
        final JsonNode jsonNullId = context.mapper().createObjectNode()
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(ID, NullNode.getInstance());
        codec.decode((ObjectNode) jsonNullId, context);
    }

    /**
     * Checks if decoding service network with invalid type fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeInvalidType() {
        final JsonNode jsonInvalidType = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, "type")
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString());
        codec.decode((ObjectNode) jsonInvalidType, context);
    }

    /**
     * Checks if decoding service network with null type fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeNullType() {
        final JsonNode jsonNullType = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(TYPE, NullNode.getInstance());
        codec.decode((ObjectNode) jsonNullType, context);
    }

    /**
     * Checks if decoding service network with invalid segment ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeInvalidSegmentId() {
        final JsonNode jsonInvalidSeg = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, "segmentId")
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString());
        codec.decode((ObjectNode) jsonInvalidSeg, context);
    }

    /**
     * Checks if decoding service network with 0 segment ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeZeroSegmentId() {
        final JsonNode jsonInvalidSeg = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, 0)
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString());
        codec.decode((ObjectNode) jsonInvalidSeg, context);
    }

    /**
     * Checks if decoding service network with 0 segment ID fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeNullSegmentId() {
        final JsonNode jsonInvalidSeg = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(SEGMENT_ID, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidSeg, context);
    }

    /**
     * Checks if decoding service network with invalid subnet fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeInvalidSubnet() {
        final JsonNode jsonInvalidSubnet = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, "")
                .put(SERVICE_IP, SERVICE_IP_1.toString());
        codec.decode((ObjectNode) jsonInvalidSubnet, context);
    }

    /**
     * Checks if decoding service network with invalid subnet fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeNullSubnet() {
        final JsonNode jsonInvalidSubnet = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(SUBNET, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidSubnet, context);
    }

    /**
     * Checks if decoding service network with invalid service IP fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeInvalidServiceIp() {
        final JsonNode jsonInvalidServiceIp = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, "");
        codec.decode((ObjectNode) jsonInvalidServiceIp, context);
    }

    /**
     * Checks if decoding service network with null service IP fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkDecodeNullServiceIp() {
        final JsonNode jsonInvalidServiceIp = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .set(SERVICE_IP, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidServiceIp, context);
    }

    /**
     * Checks if decoding service network with null and empty providers allowed.
     */
    @Test
    public void testServiceNetworkDecodeNullAndEmptyProviders() {
        JsonNode jsonInvalidProvider = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(PROVIDERS, NullNode.getInstance());
        codec.decode((ObjectNode) jsonInvalidProvider, context);

        jsonInvalidProvider = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(PROVIDERS, context.mapper().createArrayNode());
        codec.decode((ObjectNode) jsonInvalidProvider, context);
    }

    /**
     * Checks if decoding service network with non-array providers value fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkNonArrayProviders() {
        final JsonNode jsonProvider = context.mapper().createObjectNode()
                .put(ID, PROVIDER_ID_1.id())
                .put(DEP_TYPE, true);
        final JsonNode jsonInvalidProvider = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(PROVIDERS, jsonProvider);
        codec.decode((ObjectNode) jsonInvalidProvider, context);
    }

    /**
     * Checks if decoding service network with invalid provider fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkMissingProviderId() {
        final ArrayNode jsonProviders = context.mapper().createArrayNode();
        final JsonNode jsonProvider = context.mapper().createObjectNode()
                .put(DEP_TYPE, true);
        jsonProviders.add(jsonProvider);
        final JsonNode jsonInvalidProvider = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(PROVIDERS, jsonProvider);
        codec.decode((ObjectNode) jsonInvalidProvider, context);
    }

    /**
     * Checks if decoding service network with invalid provider fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkMissingProviderType() {
        final ArrayNode jsonProviders = context.mapper().createArrayNode();
        final JsonNode jsonProvider = context.mapper().createObjectNode()
                .put(ID, PROVIDER_ID_1.id());
        jsonProviders.add(jsonProvider);
        final JsonNode jsonInvalidProvider = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(PROVIDERS, jsonProvider);
        codec.decode((ObjectNode) jsonInvalidProvider, context);
    }

    /**
     * Checks if decoding service network with invalid provider fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkNullProviderId() {
        final ArrayNode jsonProviders = context.mapper().createArrayNode();
        final JsonNode jsonProvider = context.mapper().createObjectNode()
                .put(DEP_TYPE, true)
                .set(ID, NullNode.getInstance());
        jsonProviders.add(jsonProvider);
        final JsonNode jsonInvalidProvider = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(PROVIDERS, jsonProvider);
        codec.decode((ObjectNode) jsonInvalidProvider, context);
    }

    /**
     * Checks if decoding service network with invalid provider fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testServiceNetworkNullProviderType() {
        final ArrayNode jsonProviders = context.mapper().createArrayNode();
        final JsonNode jsonProvider = context.mapper().createObjectNode()
                .put(ID, PROVIDER_ID_1.id())
                .set(DEP_TYPE, NullNode.getInstance());
        jsonProviders.add(jsonProvider);
        final JsonNode jsonInvalidProvider = context.mapper().createObjectNode()
                .put(ID, ID_1.id())
                .put(NAME, NAME_1)
                .put(TYPE, PRIVATE.name())
                .put(SEGMENT_ID, SEGMENT_ID_1.id())
                .put(SUBNET, SUBNET_1.toString())
                .put(SERVICE_IP, SERVICE_IP_1.toString())
                .set(PROVIDERS, jsonProvider);
        codec.decode((ObjectNode) jsonInvalidProvider, context);
    }

    private ServiceNetwork getServiceNetwork(String resource) throws IOException {
        InputStream jsonStream = ServiceNetworkCodecTest.class.getResourceAsStream(resource);
        JsonNode jsonNode = context.mapper().readTree(jsonStream).get(SERVICE_NETWORK);
        assertThat(jsonNode, notNullValue());

        ServiceNetwork snet = codec.decode((ObjectNode) jsonNode, context);
        assertThat(snet, notNullValue());
        return snet;
    }
}
