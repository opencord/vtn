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
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.opencord.cordvtn.api.net.ProviderNetwork;
import org.opencord.cordvtn.api.net.ServiceNetwork;

import java.util.Objects;

import static org.opencord.cordvtn.api.dependency.Dependency.Type.BIDIRECTIONAL;

/**
 * Json matcher for ServiceNetwork.
 */
public final class ServiceNetworkJsonMatcher extends TypeSafeDiagnosingMatcher<JsonNode> {

    private final ServiceNetwork network;

    private ServiceNetworkJsonMatcher(ServiceNetwork network) {
        this.network = network;
    }

    /**
     * Factory to allocate ServiceNetwork matcher.
     *
     * @param network service network object to match
     * @return matcher
     */
    public static ServiceNetworkJsonMatcher matchesServiceNetwork(ServiceNetwork network) {
        return new ServiceNetworkJsonMatcher(network);
    }

    @Override
    protected boolean matchesSafely(JsonNode jsonNet, Description description) {
        String jsonNetId = jsonNet.get("id").asText();
        if (!Objects.equals(jsonNetId, network.id().id())) {
            description.appendText("network id was " + jsonNetId);
            return false;
        }

        String jsonType = jsonNet.get("type").asText().toUpperCase();
        if (!Objects.equals(jsonType, network.type().name())) {
            description.appendText("type was " + jsonType);
            return false;
        }

        if (network.providers().isEmpty()) {
            return true;
        }

        JsonNode jsonProviders = jsonNet.get("providerNetworks");
        if (jsonProviders == null || jsonProviders == NullNode.getInstance()) {
            description.appendText("provider networks were empty");
            return false;
        }

        if (jsonProviders.size() != network.providers().size()) {
            return false;
        }

        for (JsonNode provider : jsonProviders) {
            String id = provider.get("id").asText();
            boolean bidirectional = provider.get("bidirectional").asBoolean();
            ProviderNetwork proNet = network.providers().stream()
                    .filter(p -> p.id().id().equals(id))
                    .findAny().orElse(null);

            if (proNet == null) {
                final String msg = String.format("provider id:%s couldn't find", id);
                description.appendText(msg);
                return false;
            }

            if (proNet.type().equals(BIDIRECTIONAL) != bidirectional) {
                final String msg = String.format(
                        "mismatch provider id:%s, bidirectional: %s",
                        id, bidirectional);
                description.appendText(msg);
                return false;
            }
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(network.toString());
    }
}
