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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.SegmentId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType;
import org.opencord.cordvtn.impl.DefaultServiceNetwork;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType.BIDIRECTIONAL;
import static org.opencord.cordvtn.api.net.ServiceNetwork.DependencyType.UNIDIRECTIONAL;
import static org.opencord.cordvtn.api.net.ServiceNetwork.NetworkType.valueOf;

/**
 * Service network JSON codec.
 */
public final class ServiceNetworkCodec extends JsonCodec<ServiceNetwork> {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String SEGMENT_ID = "segment_id";
    private static final String SUBNET = "subnet";
    private static final String SERVICE_IP = "service_ip";
    @Deprecated
    private static final String PROVIDER_NETWORKS = "providerNetworks";
    private static final String PROVIDERS = "providers";
    private static final String DEP_TYPE = "bidirectional";

    private static final String ERR_JSON = "Invalid ServiceNetwork received";
    private static final String ERR_ID = "Service network ID cannot be null";

    @Override
    public ObjectNode encode(ServiceNetwork snet, CodecContext context) {
        ObjectNode result = context.mapper().createObjectNode().put(ID, snet.id().id());
        if (!Strings.isNullOrEmpty(snet.name())) {
            result.put(NAME, snet.name());
        }
        if (snet.type() != null) {
            result.put(TYPE, snet.type().name());
        }
        if (snet.segmentId() != null) {
            result.put(SEGMENT_ID, snet.segmentId().id());
        }
        if (snet.subnet() != null) {
            result.put(SUBNET, snet.subnet().toString());
        }
        if (snet.serviceIp() != null) {
            result.put(SERVICE_IP, snet.serviceIp().toString());
        }
        ArrayNode providers = context.mapper().createArrayNode();
        snet.providers().entrySet().forEach(provider -> {
            ObjectNode providerJson = context.mapper().createObjectNode()
                    .put(ID, provider.getKey().id())
                    .put(DEP_TYPE, provider.getValue() == BIDIRECTIONAL ? TRUE : FALSE);
            providers.add(providerJson);
        });
        result.set(PROVIDERS, providers);
        return result;
    }

    @Override
    public ServiceNetwork decode(ObjectNode json, CodecContext context) {
        validateJson(json);
        ServiceNetwork.Builder snetBuilder = DefaultServiceNetwork.builder()
                .id(NetworkId.of(json.get(ID).asText()));

        // TODO remove existing values when explicit null received
        if (json.get(NAME) != null && !json.get(NAME).isNull()) {
            snetBuilder.name(json.get(NAME).asText());
        }
        if (json.get(TYPE) != null && !json.get(TYPE).isNull()) {
            snetBuilder.type(valueOf(json.get(TYPE).asText().toUpperCase()));
        }
        if (json.get(SEGMENT_ID) != null && !json.get(SEGMENT_ID).isNull()) {
            snetBuilder.segmentId(SegmentId.of(json.get(SEGMENT_ID).asLong()));
        }
        if (json.get(SUBNET) != null && !json.get(SUBNET).isNull()) {
            snetBuilder.subnet(IpPrefix.valueOf(json.get(SUBNET).asText()));
        }
        if (json.get(SERVICE_IP) != null && !json.get(SERVICE_IP).isNull()) {
            snetBuilder.serviceIp(IpAddress.valueOf(json.get(SERVICE_IP).asText()));
        }
        if (json.get(PROVIDERS) != null) {
            if (json.get(PROVIDERS).isNull()) {
                snetBuilder.providers(ImmutableMap.of());
            } else {
                Map<NetworkId, DependencyType> providers = Maps.newHashMap();
                json.get(PROVIDERS).forEach(provider -> {
                    DependencyType type = provider.get(DEP_TYPE).asBoolean() ?
                            BIDIRECTIONAL : UNIDIRECTIONAL;
                    providers.put(NetworkId.of(provider.get(ID).asText()), type);
                });
                snetBuilder.providers(providers);
            }
        }
        if (json.get(PROVIDER_NETWORKS) != null) {
            if (json.get(PROVIDER_NETWORKS).isNull()) {
                snetBuilder.providers(ImmutableMap.of());
            } else {
                Map<NetworkId, DependencyType> providers = Maps.newHashMap();
                json.get(PROVIDER_NETWORKS).forEach(provider -> {
                    DependencyType type = provider.get(DEP_TYPE).asBoolean() ?
                            BIDIRECTIONAL : UNIDIRECTIONAL;
                    providers.put(NetworkId.of(provider.get(ID).asText()), type);
                });
                snetBuilder.providers(providers);
            }
        }
        return snetBuilder.build();
    }

    private void validateJson(ObjectNode json) {
        checkArgument(json != null && json.isObject(), ERR_JSON);
        checkArgument(json.get(ID) != null && !json.get(ID).isNull(), ERR_ID);
        checkArgument(!Strings.isNullOrEmpty(json.get(ID).asText()), ERR_ID);

        // allow explicit null for removing the existing value
        if (json.get(NAME) != null && !json.get(NAME).isNull()) {
            if (Strings.isNullOrEmpty(json.get(NAME).asText())) {
                final String error = "Null or empty ServiceNetwork name received";
                throw new IllegalArgumentException(error);
            }
        }

        if (json.get(TYPE) != null && !json.get(TYPE).isNull()) {
            try {
                valueOf(json.get(TYPE).asText().toUpperCase());
            } catch (IllegalArgumentException e) {
                final String error = "Invalid ServiceNetwork type received: ";
                throw new IllegalArgumentException(error + json.get(TYPE).asText());
            }
        }

        if (json.get(SEGMENT_ID) != null && !json.get(SEGMENT_ID).isNull()) {
            if (json.get(SEGMENT_ID).asLong() == 0) {
                final String error = "Invalid ServiecNetwork segment ID received: ";
                throw new IllegalArgumentException(error + json.get(SEGMENT_ID).asText());
            }
        }

        if (json.get(SUBNET) != null && !json.get(SUBNET).isNull()) {
            try {
                IpPrefix.valueOf(json.get(SUBNET).asText());
            } catch (IllegalArgumentException e) {
                final String error = "Invalid ServiceNetwork subnet received: ";
                throw new IllegalArgumentException(error + json.get(SUBNET).asText());
            }
        }

        if (json.get(SERVICE_IP) != null && !json.get(SERVICE_IP).isNull()) {
            try {
                IpAddress.valueOf(json.get(SERVICE_IP).asText());
            } catch (IllegalArgumentException e) {
                final String error = "Invalid ServiceNetwork service IP address received: ";
                throw new IllegalArgumentException(error + json.get(SERVICE_IP).asText());
            }
        }

        if (json.get(PROVIDERS) != null && !json.get(PROVIDERS).isNull()) {
            json.get(PROVIDERS).forEach(provider -> {
                if (provider.get(ID) == null || provider.get(ID).isNull() ||
                        Strings.isNullOrEmpty(provider.get(ID).asText())) {
                    final String error = "Null or empty provider network ID received";
                    throw new IllegalArgumentException(error);
                }

                if (provider.get(DEP_TYPE) == null || provider.get(DEP_TYPE).isNull()
                        || !provider.get(DEP_TYPE).isBoolean()) {
                    final String error = "Non-boolean bidirectional received";
                    throw new IllegalArgumentException(error);
                }
            });
        }

        if (json.get(PROVIDER_NETWORKS) != null && !json.get(PROVIDER_NETWORKS).isNull()) {
            json.get(PROVIDER_NETWORKS).forEach(provider -> {
                if (provider.get(ID) == null || provider.get(ID).isNull() ||
                        Strings.isNullOrEmpty(provider.get(ID).asText())) {
                    final String error = "Null or empty provider network ID received";
                    throw new IllegalArgumentException(error);
                }

                if (provider.get(DEP_TYPE) == null || provider.get(DEP_TYPE).isNull()
                        || !provider.get(DEP_TYPE).isBoolean()) {
                    final String error = "Non-boolean bidirectional received";
                    throw new IllegalArgumentException(error);
                }
            });
        }
    }
}
