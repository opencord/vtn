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
package org.opencord.cordvtn.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.ProviderNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork;

import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.opencord.cordvtn.api.dependency.Dependency.Type.BIDIRECTIONAL;
import static org.opencord.cordvtn.api.net.ServiceNetwork.ServiceNetworkType.MANAGEMENT_LOCAL;
import static org.opencord.cordvtn.api.net.ServiceNetwork.ServiceNetworkType.PRIVATE;
import static org.opencord.cordvtn.codec.ServiceNetworkJsonMatcher.matchesServiceNetwork;

/**
 * Unit tests for ServiceNetwork codec.
 */
public final class ServiceNetworkCodecTest {
    private static final String SERVICE_NETWORK = "serviceNetwork";
    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String PROVIDER_NETWORKS = "providerNetworks";

    private final ProviderNetwork providerA =
            ProviderNetwork.of(NetworkId.of("A"), BIDIRECTIONAL);

    private final ServiceNetwork networkB = new ServiceNetwork(
            NetworkId.of("A"),
            MANAGEMENT_LOCAL,
            ImmutableSet.of());

    private final ServiceNetwork networkA = new ServiceNetwork(
            NetworkId.of("B"),
            PRIVATE,
            ImmutableSet.of(providerA));

    @Rule
    public ExpectedException exception = ExpectedException.none();

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

    @Test
    public void testServiceNetworkEncode() {
        ObjectNode networkJson = codec.encode(networkA, context);
        assertThat(networkJson, notNullValue());
        assertThat(networkJson, matchesServiceNetwork(networkA));

        networkJson = codec.encode(networkB, context);
        assertThat(networkJson, notNullValue());
        assertThat(networkJson, matchesServiceNetwork(networkB));
    }

    @Test
    public void testServiceNetworkDecode() throws IOException {
        ServiceNetwork snet = getServiceNetwork("service-network.json");
        assertThat(snet.id(), is(NetworkId.of("A")));
        assertThat(snet.type(), is(MANAGEMENT_LOCAL));
        assertThat(snet.providers(), is(ImmutableSet.of()));

        snet = getServiceNetwork("service-network-with-provider.json");
        assertThat(snet.id(), is(NetworkId.of("B")));
        assertThat(snet.type(), is(PRIVATE));
        assertThat(snet.providers(), is(ImmutableSet.of(providerA)));
    }

    @Test
    public void testServiceNetworkDecodeMissingId() throws IllegalArgumentException {
        final JsonNode jsonMissingId = context.mapper().createObjectNode()
                .put(TYPE, PRIVATE.name())
                .put(PROVIDER_NETWORKS, "");
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonMissingId, context);
    }

    @Test
    public void testServiceNetworkDecodeEmptyId() throws IllegalArgumentException {
        final JsonNode jsonEmptyId = context.mapper().createObjectNode()
                .put(ID, "")
                .put(TYPE, PRIVATE.name())
                .put(PROVIDER_NETWORKS, "");
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonEmptyId, context);
    }

    @Test
    public void testServiceNetworkDecodeNullId() throws NullPointerException {
        final JsonNode jsonNullId = context.mapper().createObjectNode()
                .put(TYPE, PRIVATE.name())
                .put(PROVIDER_NETWORKS, "")
                .set(ID, NullNode.getInstance());
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonNullId, context);
    }

    @Test
    public void testServiceNetworkDecodeMissingType() throws IllegalArgumentException {
        final JsonNode jsonMissingType = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(PROVIDER_NETWORKS, "");
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonMissingType, context);
    }

    @Test
    public void testServiceNetworkDecodeEmptyType() throws IllegalArgumentException {
        final JsonNode jsonEmptyType = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(TYPE, "")
                .put(PROVIDER_NETWORKS, "");
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonEmptyType, context);
    }

    @Test
    public void testServiceNetworkDecodeNullType() throws IllegalArgumentException {
        final JsonNode jsonNullType = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(PROVIDER_NETWORKS, "")
                .set(TYPE, NullNode.getInstance());
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonNullType, context);
    }

    @Test
    public void testServiceNetworkDecodeWrongType() throws IllegalArgumentException {
        final JsonNode jsonNoneType = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(TYPE, "none")
                .put(PROVIDER_NETWORKS, "");
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonNoneType, context);
    }

    @Test
    public void testServiceNetworkDecodeWithMissingProviderId() throws NullPointerException {
        final JsonNode jsonMissingProviderId = context.mapper().createObjectNode()
                .put(TYPE, "B");
        final JsonNode jsonWithProvider = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(TYPE, PRIVATE.name())
                .set(PROVIDER_NETWORKS, jsonMissingProviderId);
        exception.expect(NullPointerException.class);
        codec.decode((ObjectNode) jsonWithProvider, context);
    }

    @Test
    public void testServiceNetworkDecodeWithWrongProviderType() throws NullPointerException {
        final JsonNode jsonWrongProviderType = context.mapper().createObjectNode()
                .put(ID, "B")
                .put(TYPE, "none");
        final JsonNode jsonWithProvider = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(TYPE, PRIVATE.name())
                .set(PROVIDER_NETWORKS, jsonWrongProviderType);
        exception.expect(NullPointerException.class);
        codec.decode((ObjectNode) jsonWithProvider, context);
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
