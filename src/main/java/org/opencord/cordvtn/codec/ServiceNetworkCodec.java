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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.dependency.Dependency;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.ProviderNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork.ServiceNetworkType;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.opencord.cordvtn.api.dependency.Dependency.Type.BIDIRECTIONAL;
import static org.opencord.cordvtn.api.dependency.Dependency.Type.UNIDIRECTIONAL;
import static org.opencord.cordvtn.api.net.ServiceNetwork.ServiceNetworkType.valueOf;

/**
 * Service network JSON codec.
 */
public final class ServiceNetworkCodec extends JsonCodec<ServiceNetwork> {

    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String PROVIDER_NETWORKS = "providerNetworks";
    private static final String BIDIRECT = "bidirectional";

    private static final String ERR_JSON = "Invalid ServiceNetwork received";
    private static final String ERR_ID = ": network ID cannot be null";
    private static final String ERR_TYPE = ": type cannot be null";

    @Override
    public ObjectNode encode(ServiceNetwork snet, CodecContext context) {
        ObjectNode result = context.mapper().createObjectNode()
                .put(ID, snet.id().id())
                .put(TYPE, snet.type().name().toLowerCase());

        ArrayNode providers = context.mapper().createArrayNode();
        snet.providers().forEach(provider -> {
            ObjectNode providerJson = context.mapper().createObjectNode()
                    .put(ID, provider.id().id())
                    .put(BIDIRECT, provider.type() == BIDIRECTIONAL ? TRUE : FALSE);
            providers.add(providerJson);
        });

        result.set(PROVIDER_NETWORKS, providers);
        return result;
    }

    @Override
    public ServiceNetwork decode(ObjectNode json, CodecContext context) {
        checkArgument(json != null && json.isObject(), ERR_JSON);
        checkArgument(json.get(ID) != null &&
                              json.get(ID) != NullNode.getInstance(),
                      ERR_JSON + ERR_ID);
        checkArgument(json.get(TYPE) != null &&
                              json.get(TYPE) != NullNode.getInstance(),
                      ERR_JSON + ERR_TYPE);

        NetworkId netId = NetworkId.of(json.get(ID).asText());
        ServiceNetworkType netType = valueOf(json.get(TYPE).asText().toUpperCase());
        Set<ProviderNetwork> providers = Sets.newHashSet();
        if (json.get(PROVIDER_NETWORKS) != null) {
            json.get(PROVIDER_NETWORKS).forEach(provider -> {
                NetworkId providerId = NetworkId.of(provider.get(ID).asText());
                Dependency.Type type = provider.get(BIDIRECT).asBoolean() ?
                        BIDIRECTIONAL : UNIDIRECTIONAL;
                providers.add(ProviderNetwork.of(providerId, type));
            });
        }
        return new ServiceNetwork(netId, netType, providers);
    }
}
