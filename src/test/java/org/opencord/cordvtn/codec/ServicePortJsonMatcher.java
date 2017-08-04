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
import com.fasterxml.jackson.databind.node.NullNode;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.opencord.cordvtn.api.net.AddressPair;
import org.opencord.cordvtn.api.net.ServicePort;

import java.util.Objects;

/**
 * Json matcher for ServicePort.
 */
public final class ServicePortJsonMatcher extends TypeSafeDiagnosingMatcher<JsonNode> {

    private final ServicePort port;

    private ServicePortJsonMatcher(ServicePort port) {
        this.port = port;
    }

    /**
     * Factory to allocate ServicePort matcher.
     *
     * @param port service port object to match
     * @return matcher
     */
    public static ServicePortJsonMatcher matchesServicePort(ServicePort port) {
        return new ServicePortJsonMatcher(port);
    }

    @Override
    protected boolean matchesSafely(JsonNode jsonNode, Description description) {
        final String jsonPortId = jsonNode.get("id").asText();
        if (!Objects.equals(jsonPortId, port.id().id())) {
            description.appendText("Port id was " + jsonPortId);
            return false;
        }

        final String jsonPortName = jsonNode.get("name").asText();
        if (!Objects.equals(jsonPortName, port.name())) {
            description.appendText("Port name was " + jsonPortName);
            return false;
        }

        final String jsonPortNetId = jsonNode.get("network_id").asText();
        if (!Objects.equals(jsonPortNetId, port.networkId().id())) {
            description.appendText("Network id was " + jsonPortNetId);
            return false;
        }

        final String jsonMacAddr = jsonNode.get("mac_address").asText();
        if (!Objects.equals(jsonMacAddr, port.mac().toString())) {
            description.appendText("MAC address was " + jsonMacAddr);
            return false;
        }

        final String jsonIpAddr = jsonNode.get("ip_address").asText();
        if (!Objects.equals(jsonIpAddr, port.ip().toString())) {
            description.appendText("IP address was " + jsonIpAddr);
            return false;
        }

        final String jsonVlanId = jsonNode.get("vlan_id").asText();
        if (!Objects.equals(jsonVlanId, port.vlanId().toString())) {
            description.appendText("VLAN id was " + jsonVlanId);
            return false;
        }

        final JsonNode jsonAddrPairs = jsonNode.get("floating_address_pairs");
        if (port.addressPairs().isEmpty()) {
            if (jsonAddrPairs != null &&
                    jsonAddrPairs != NullNode.getInstance() &&
                    jsonAddrPairs.size() != 0) {
                description.appendText("Floating address pairs did not match");
                return false;
            }
        } else {
            if (jsonAddrPairs == null ||
                    jsonAddrPairs == NullNode.getInstance() ||
                    jsonAddrPairs.size() == 0) {
                description.appendText("Floating address pairs was empty");
                return false;
            } else if (jsonAddrPairs.size() != port.addressPairs().size()) {
                description.appendText("Floating address pairs size was " +
                        jsonAddrPairs.size());
                return false;
            } else {
                for (JsonNode addrPair : jsonAddrPairs) {
                    final AddressPair tmp = AddressPair.of(
                            IpAddress.valueOf(addrPair.get("ip_address").asText()),
                            MacAddress.valueOf(addrPair.get("mac_address").asText())
                    );
                    if (!port.addressPairs().contains(tmp)) {
                        description.appendText("Floating address pairs did not match " + tmp);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(port.toString());
    }
}
