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
import com.google.common.collect.Sets;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.opencord.cordvtn.api.net.AddressPair;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServicePort;

import java.util.Set;

/**
 * Service port JSON codec.
 */
public final class ServicePortCodec extends JsonCodec<ServicePort> {

    private static final String ID = "id";
    private static final String VLAN_ID = "vlan_id";
    private static final String FLOATING_ADDRESS_PAIRS = "floating_address_pairs";
    private static final String IP_ADDRESS = "ip_address";
    private static final String MAC_ADDRESS = "mac_address";

    @Override
    public ObjectNode encode(ServicePort sport, CodecContext context) {
        ObjectNode result = context.mapper().createObjectNode()
                .put(ID, sport.id().id());
        if (sport.vlanId().isPresent()) {
            result.put(VLAN_ID, sport.vlanId().get().id());
        }

        ArrayNode addressPairs = context.mapper().createArrayNode();
        sport.addressPairs().forEach(pair -> {
            ObjectNode pairJson = context.mapper().createObjectNode()
                    .put(IP_ADDRESS, pair.ip().toString())
                    .put(MAC_ADDRESS, pair.mac().toString());
            addressPairs.add(pairJson);
        });
        result.set(FLOATING_ADDRESS_PAIRS, addressPairs);
        return result;
    }

    @Override
    public ServicePort decode(ObjectNode json, CodecContext context) {
        if (json == null || !json.isObject()) {
            return null;
        }

        PortId portId = PortId.of(json.get(ID).asText());
        VlanId vlanId = null;
        if (json.get(VLAN_ID) != null) {
            try {
                vlanId = VlanId.vlanId(json.get(VLAN_ID).asText());
            } catch (Exception ignore) {
            }
        }

        Set<AddressPair> addressPairs = Sets.newHashSet();
        if (json.get(FLOATING_ADDRESS_PAIRS) != null) {
            json.get(FLOATING_ADDRESS_PAIRS).forEach(pair -> {
                AddressPair addrPair = AddressPair.of(
                        IpAddress.valueOf(pair.get(IP_ADDRESS).asText()),
                        MacAddress.valueOf(pair.get(MAC_ADDRESS).asText()));
                addressPairs.add(addrPair);
            });
        }
        return new ServicePort(portId, vlanId, addressPairs);
    }
}
