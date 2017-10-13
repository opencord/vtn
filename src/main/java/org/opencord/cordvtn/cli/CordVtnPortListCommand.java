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
package org.opencord.cordvtn.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.cordvtn.api.core.ServiceNetworkService;
import org.opencord.cordvtn.api.net.ServicePort;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

/**
 * Lists VTN networks.
 */
@Command(scope = "onos", name = "cordvtn-ports",
        description = "Lists all VTN ports")
public class CordVtnPortListCommand extends AbstractShellCommand {

    private static final String FORMAT = "%-40s%-40s%-30s%-20s%-18s%-10s%s";

    @Argument(name = "networkId", description = "Network ID")
    private String networkId = null;

    @Override
    protected void execute() {
        ServiceNetworkService service = AbstractShellCommand.get(ServiceNetworkService.class);

        List<ServicePort> ports = Lists.newArrayList(service.servicePorts());
        ports.sort(Comparator.comparing(port -> port.networkId().id()));
        if (!Strings.isNullOrEmpty(networkId)) {
            ports.removeIf(port -> !port.networkId().id().equals(networkId));
        }

        if (outputJson()) {
            try {
                print("%s", mapper().writeValueAsString(json(ports)));
            } catch (JsonProcessingException e) {
                print("Failed to list networks in JSON format");
            }
        } else {
            print(FORMAT, "ID", "Network ID", "Name", "MAC", "IP", "VLAN", "WAN IPs");
            for (ServicePort port: ports) {
                List<String> floatingIps = port.addressPairs().stream()
                        .map(ip -> ip.ip().toString())
                        .collect(Collectors.toList());
                print(FORMAT, port.id(),
                        port.networkId(),
                        port.name(),
                        port.mac(),
                        port.ip(),
                        port.vlanId() != null ? port.vlanId() : "",
                        floatingIps.isEmpty() ? "" : floatingIps);
            }
        }
    }

    private JsonNode json(List<ServicePort> ports) {
        ArrayNode result = mapper().enable(INDENT_OUTPUT).createArrayNode();
        for (ServicePort port: ports) {
            ArrayNode addrPairs = mapper().createArrayNode();
            port.addressPairs().forEach(pair -> addrPairs.add(
                    mapper().createObjectNode()
                            .put("ip", pair.ip().toString())
                            .put("mac", pair.mac().toString())));

            result.add(mapper().createObjectNode()
                    .put("id", port.id().id())
                    .put("name", port.name())
                    .put("networkId", port.networkId().id())
                    .put("mac", port.mac().toString())
                    .put("ip", port.ip().toString())
                    .put("vlan", port.vlanId() != null ?
                            port.vlanId().toString() : null)
                    .set("addressPairs", addrPairs));
        }
        return result;
    }
}
