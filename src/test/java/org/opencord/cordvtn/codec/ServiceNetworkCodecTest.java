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
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.net.NetworkId;
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
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.MANAGEMENT_LOCAL;
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

    private final Map<NetworkId, DependencyType> providerA =
            new HashMap<NetworkId, DependencyType>() {
                {
                    put(NetworkId.of("A"), BIDIRECTIONAL);
                }
            };

    private final ServiceNetwork networkA = DefaultServiceNetwork.builder()
            .id(NetworkId.of("A"))
            .name("A")
            .type(MANAGEMENT_LOCAL)
            .build();

    private final ServiceNetwork networkB = DefaultServiceNetwork.builder()
            .id(NetworkId.of("B"))
            .name("B")
            .type(PRIVATE)
            .providers(providerA)
            .build();

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
        assertThat(snet.name(), is("A"));
        assertThat(snet.type(), is(MANAGEMENT_LOCAL));
        assertThat(snet.providers(), is(ImmutableMap.of()));

        snet = getServiceNetwork("service-network-with-provider.json");
        assertThat(snet.id(), is(NetworkId.of("B")));
        assertThat(snet.name(), is("B"));
        assertThat(snet.type(), is(PRIVATE));
        assertThat(snet.providers(), is(providerA));
    }

    @Test
    public void testServiceNetworkDecodeMissingId() throws IllegalArgumentException {
        final JsonNode jsonMissingId = context.mapper().createObjectNode()
                .put(NAME, "A")
                .put(TYPE, PRIVATE.name())
                .put(PROVIDERS, "");
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonMissingId, context);
    }

    @Test
    public void testServiceNetworkDecodeEmptyId() throws IllegalArgumentException {
        final JsonNode jsonEmptyId = context.mapper().createObjectNode()
                .put(ID, "")
                .put(NAME, "A")
                .put(TYPE, PRIVATE.name())
                .put(PROVIDERS, "");
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonEmptyId, context);
    }

    @Test
    public void testServiceNetworkDecodeNullId() throws NullPointerException {
        final JsonNode jsonNullId = context.mapper().createObjectNode()
                .put(NAME, "A")
                .put(TYPE, PRIVATE.name())
                .put(PROVIDERS, "")
                .set(ID, NullNode.getInstance());
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonNullId, context);
    }

    @Test
    public void testServiceNetworkDecodeWithMissingProviderId() throws NullPointerException {
        final JsonNode jsonMissingProviderId = context.mapper().createObjectNode()
                .put(TYPE, "B");
        final JsonNode jsonWithProvider = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(NAME, "A")
                .put(TYPE, PRIVATE.name())
                .set(PROVIDERS, jsonMissingProviderId);
        exception.expect(IllegalArgumentException.class);
        codec.decode((ObjectNode) jsonWithProvider, context);
    }

    @Test
    public void testServiceNetworkDecodeWithWrongProviderType() throws NullPointerException {
        final JsonNode jsonWrongProviderType = context.mapper().createObjectNode()
                .put(ID, "B")
                .put(TYPE, "none");
        final JsonNode jsonWithProvider = context.mapper().createObjectNode()
                .put(ID, "A")
                .put(NAME, "A")
                .put(TYPE, PRIVATE.name())
                .set(PROVIDERS, jsonWrongProviderType);
        exception.expect(IllegalArgumentException.class);
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
