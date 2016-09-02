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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.NetworkId;
import org.opencord.cordvtn.api.ServiceNetwork;
import org.opencord.cordvtn.api.ServiceNetwork.DirectAccessType;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.opencord.cordvtn.api.ServiceNetwork.DirectAccessType.BIDIRECTIONAL;
import static org.opencord.cordvtn.api.ServiceNetwork.DirectAccessType.UNIDIRECTIONAL;
import static org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType.valueOf;

/**
 * Service network JSON codec.
 */
public final class ServiceNetworkCodec extends JsonCodec<ServiceNetwork> {

    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String PROVIDER_NETWORKS = "providerNetworks";
    private static final String BIDIRECT = "bidirectional";

    @Override
    public ObjectNode encode(ServiceNetwork snet, CodecContext context) {
        ObjectNode result = context.mapper().createObjectNode()
                .put(ID, snet.id().id())
                .put(TYPE, snet.type().name().toLowerCase());

        ArrayNode providers = context.mapper().createArrayNode();
        snet.providers().entrySet().forEach(provider -> {
            ObjectNode providerJson = context.mapper().createObjectNode()
                    .put(ID, provider.getKey().id())
                    .put(BIDIRECT, provider.getValue() == BIDIRECTIONAL ? TRUE : FALSE);
            providers.add(providerJson);
        });

        result.set(PROVIDER_NETWORKS, providers);
        return result;
    }

    @Override
    public ServiceNetwork decode(ObjectNode json, CodecContext context) {
        if (json == null || !json.isObject()) {
            return null;
        }

        ServiceNetwork.Builder snetBuilder = ServiceNetwork.builder()
                .id(NetworkId.of(json.get(ID).asText()))
                .type(valueOf(json.get(TYPE).asText().toUpperCase()));

        if (json.get(PROVIDER_NETWORKS) != null) {
            json.get(PROVIDER_NETWORKS).forEach(provider -> {
                NetworkId providerId = NetworkId.of(provider.get(ID).asText());
                DirectAccessType type = provider.get(BIDIRECT).asBoolean() ?
                        BIDIRECTIONAL : UNIDIRECTIONAL;
                snetBuilder.addProvider(providerId, type);
            });
        }
        return snetBuilder.build();
    }
}
