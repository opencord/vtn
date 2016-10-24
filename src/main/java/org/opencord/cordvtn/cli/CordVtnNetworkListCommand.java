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
package org.opencord.cordvtn.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.cordvtn.api.core.CordVtnService;
import org.opencord.cordvtn.api.net.VtnNetwork;

import java.util.Set;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

/**
 * Lists VTN networks.
 */
@Command(scope = "onos", name = "cordvtn-networks",
        description = "Lists all VTN networks")
public class CordVtnNetworkListCommand extends AbstractShellCommand {

    private static final String FORMAT = "%-40s%-20s%-8s%-20s%s";

    @Override
    protected void execute() {
        CordVtnService service = AbstractShellCommand.get(CordVtnService.class);
        Set<VtnNetwork> networks = service.vtnNetworks();

        if (outputJson()) {
            try {
                print("%s", mapper().writeValueAsString(json(networks)));
            } catch (JsonProcessingException e) {
                print("Failed to list networks in JSON format");
            }
        } else {
            print(FORMAT, "ID", "Type", "VNI", "Subnet", "Service IP");
            for (VtnNetwork net: networks) {
                print(FORMAT, net.id(),
                      net.type(),
                      net.segmentId(),
                      net.subnet(),
                      net.serviceIp());
            }
        }
    }

    private JsonNode json(Set<VtnNetwork> networks) {
        ArrayNode result = mapper().enable(INDENT_OUTPUT).createArrayNode();
        for (VtnNetwork net: networks) {
            ArrayNode providers = mapper().createArrayNode();
            net.providers().forEach(provider -> providers.add(
                    mapper().createObjectNode()
                            .put("networkId", provider.id().id())
                            .put("type", provider.type().name())));

            result.add(mapper().createObjectNode()
                               .put("id", net.id().id())
                               .put("type", net.type().name())
                               .put("vni", net.segmentId().id())
                               .put("subnet", net.subnet().toString())
                               .put("serviceIp", net.serviceIp().toString())
                               .set("providers", providers));
        }
        return result;
    }
}
