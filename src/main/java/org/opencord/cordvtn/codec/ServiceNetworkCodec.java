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
import static com.google.common.base.Strings.isNullOrEmpty;
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

    // TODO allow removing existing value when explicit null received
    @Override
    public ServiceNetwork decode(ObjectNode json, CodecContext context) {
        checkArgument(json != null && json.isObject(), ERR_JSON);
        checkArgument(!json.path(ID).isMissingNode() && !json.path(ID).isNull(), ERR_ID);
        checkArgument(!Strings.isNullOrEmpty(json.path(ID).asText()), ERR_ID);

        ServiceNetwork.Builder snetBuilder = DefaultServiceNetwork.builder()
                .id(NetworkId.of(json.get(ID).asText()));

        if (!json.path(NAME).isMissingNode()) {
            if (json.path(NAME).isNull() || isNullOrEmpty(json.path(NAME).asText())) {
                final String error = "Null or empty ServiceNetwork name received";
                throw new IllegalArgumentException(error);
            } else {
                snetBuilder.name(json.get(NAME).asText());
            }
        }

        if (!json.path(TYPE).isMissingNode()) {
            try {
                snetBuilder.type(valueOf(json.get(TYPE).asText().toUpperCase()));
            } catch (IllegalArgumentException | NullPointerException e) {
                final String error = "Invalid ServiceNetwork type received";
                throw new IllegalArgumentException(error);
            }
        }

        if (!json.path(SEGMENT_ID).isMissingNode()) {
            try {
                snetBuilder.segmentId(SegmentId.of(json.path(SEGMENT_ID).asLong()));
            } catch (IllegalArgumentException | NullPointerException e) {
                final String error = "Invalid ServiecNetwork segment ID received";
                throw new IllegalArgumentException(error);
            }
        }

        if (!json.path(SUBNET).isMissingNode()) {
            try {
                snetBuilder.subnet(IpPrefix.valueOf(json.path(SUBNET).asText()));
            } catch (IllegalArgumentException | NullPointerException e) {
                final String error = "Invalid ServiceNetwork subnet received";
                throw new IllegalArgumentException(error);
            }
        }

        if (!json.path(SERVICE_IP).isMissingNode()) {
            try {
                snetBuilder.serviceIp(IpAddress.valueOf(json.path(SERVICE_IP).asText()));
            } catch (IllegalArgumentException | NullPointerException e) {
                final String error = "Invalid ServiceNetwork service IP address received";
                throw new IllegalArgumentException(error);
            }
        }

        if (!json.path(PROVIDERS).isMissingNode() && json.path(PROVIDERS).isNull()) {
            snetBuilder.providers(ImmutableMap.of());
        } else if (!json.path(PROVIDERS).isMissingNode()) {
            Map<NetworkId, DependencyType> providers = Maps.newHashMap();
            json.path(PROVIDERS).forEach(provider -> {
                if (provider.path(ID).isMissingNode() ||
                        provider.path(ID).isNull() ||
                        Strings.isNullOrEmpty(provider.path(ID).asText()) ||
                        provider.path(DEP_TYPE).isMissingNode() ||
                        provider.path(DEP_TYPE).isNull()) {
                    final String error = "Invalid provider received: ";
                    throw new IllegalArgumentException(error + provider.asText());
                }

                try {
                    DependencyType type = provider.path(DEP_TYPE).asBoolean() ?
                            BIDIRECTIONAL : UNIDIRECTIONAL;
                    providers.put(NetworkId.of(provider.path(ID).asText()), type);
                } catch (IllegalArgumentException e) {
                    final String error = "Invalid provider received: ";
                    throw new IllegalArgumentException(error + provider.asText());
                }
            });
            snetBuilder.providers(providers);
        }

        if (!json.path(PROVIDER_NETWORKS).isMissingNode() &&
                json.path(PROVIDER_NETWORKS).isNull()) {
            snetBuilder.providers(ImmutableMap.of());
        } else if (!json.path(PROVIDER_NETWORKS).isMissingNode()) {
            Map<NetworkId, DependencyType> providers = Maps.newHashMap();
            json.path(PROVIDER_NETWORKS).forEach(provider -> {
                if (provider.path(ID).isMissingNode() ||
                        provider.path(ID).isNull() ||
                        Strings.isNullOrEmpty(provider.path(ID).asText()) ||
                        provider.path(DEP_TYPE).isMissingNode() ||
                        provider.path(DEP_TYPE).isNull()) {
                    final String error = "Invalid provider received: ";
                    throw new IllegalArgumentException(error + provider.asText());
                }

                try {
                    DependencyType type = provider.path(DEP_TYPE).asBoolean() ?
                            BIDIRECTIONAL : UNIDIRECTIONAL;
                    providers.put(NetworkId.of(provider.path(ID).asText()), type);
                } catch (IllegalArgumentException e) {
                    final String error = "Invalid provider received: ";
                    throw new IllegalArgumentException(error + provider.asText());
                }
            });
            snetBuilder.providers(providers);
        }
        return snetBuilder.build();
    }
}
