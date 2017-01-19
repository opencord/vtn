/*
 * Copyright 2017-present Open Networking Laboratory
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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.cordvtn.api.core.ServiceNetworkAdminService;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.rest.XosVtnNetworkingClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Synchronizes network states with XOS VTN service.
 * This command can be used to actively synchronize XOS network with VTN
 * service network.
 */
@Command(scope = "onos", name = "cordvtn-sync-xos-states",
        description = "Synchronizes network states with Neutron")
public class CordVtnSyncXosStatesCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "endpoint", description = "XOS VTN service endpoint",
            required = true, multiValued = false)
    private String endpoint = null;

    @Argument(index = 1, name = "user", description = "XOS admin user name",
            required = true, multiValued = false)
    private String user = null;

    @Argument(index = 2, name = "password", description = "XOS admin user password",
            required = true, multiValued = false)
    private String password = null;

    private static final String NET_FORMAT = "%-40s%-20s%-20s%-8s%-20s%s";
    private static final String PORT_FORMAT = "%-40s%-20s%-18s%-8s%s";

    @Override
    protected void execute() {
        ServiceNetworkAdminService snetService =
                AbstractShellCommand.get(ServiceNetworkAdminService.class);

        XosVtnNetworkingClient client = XosVtnNetworkingClient.builder()
                .endpoint(endpoint)
                .user(user)
                .password(password)
                .build();

        print("Synchronizing service networks...");
        print(NET_FORMAT, "ID", "Name", "Type", "VNI", "Subnet", "Service IP");
        client.serviceNetworks().forEach(snet -> {
            if (snetService.serviceNetwork(snet.id()) != null) {
                snetService.updateServiceNetwork(snet);
            } else {
                snetService.createServiceNetwork(snet);
            }
            ServiceNetwork updated = snetService.serviceNetwork(snet.id());
            print(NET_FORMAT, updated.id(),
                  updated.name(),
                  updated.type(),
                  updated.segmentId(),
                  updated.subnet(),
                  updated.serviceIp());
        });

        // FIXME creating a port fails until XOS service API provides network ID
        print("\nSynchronizing service ports...");
        print(PORT_FORMAT, "ID", "MAC", "IP", "VLAN", "WAN IPs");
        client.servicePorts().forEach(sport -> {
            if (snetService.servicePort(sport.id()) != null) {
                snetService.updateServicePort(sport);
            } else {
                snetService.createServicePort(sport);
            }
            ServicePort updated = snetService.servicePort(sport.id());
            List<String> floatingIps = updated.addressPairs().stream()
                    .map(ip -> ip.ip().toString())
                    .collect(Collectors.toList());
            print(PORT_FORMAT, updated.id(),
                  updated.mac(),
                  updated.ip(),
                  updated.vlanId() != null ? updated.vlanId() : "",
                  floatingIps.isEmpty() ? "" : floatingIps);
        });
    }
}
