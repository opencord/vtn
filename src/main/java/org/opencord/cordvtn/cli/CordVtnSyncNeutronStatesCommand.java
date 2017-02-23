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
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.cordvtn.api.core.ServiceNetworkAdminService;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.SegmentId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;
import org.opencord.cordvtn.impl.DefaultServiceNetwork;
import org.opencord.cordvtn.impl.DefaultServicePort;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.identity.Access;
import org.openstack4j.openstack.OSFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Synchronizes network states with OpenStack Neutron.
 * This command can be used to actively synchronize Neutron network with VTN
 * service network.
 */
@Command(scope = "onos", name = "cordvtn-sync-neutron-states",
        description = "Synchronizes network states with Neutron")
public class CordVtnSyncNeutronStatesCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "endpoint", description = "OpenStack service endpoint",
            required = true, multiValued = false)
    private String endpoint = null;

    @Argument(index = 1, name = "tenant", description = "OpenStack admin tenant name",
            required = true, multiValued = false)
    private String tenant = null;

    @Argument(index = 2, name = "user", description = "OpenStack admin user name",
            required = true, multiValued = false)
    private String user = null;

    @Argument(index = 3, name = "password", description = "OpenStack admin user password",
            required = true, multiValued = false)
    private String password = null;

    private static final String PORT_NAME_PREFIX = "tap";
    private static final String NET_FORMAT = "%-40s%-30s%-20s%-8s%-20s%s";
    private static final String PORT_FORMAT = "%-40s%-30s%-20s%-18s%-10s%s";

    @Override
    protected void execute() {
        ServiceNetworkAdminService snetService =
                AbstractShellCommand.get(ServiceNetworkAdminService.class);
        Access osAccess;
        try {
            osAccess = OSFactory.builder()
                    .endpoint(this.endpoint)
                    .tenantName(this.tenant)
                    .credentials(this.user, this.password)
                    .authenticate()
                    .getAccess();
        } catch (AuthenticationException e) {
            print("Authentication failed");
            return;
        } catch (Exception e) {
            print("OpenStack service endpoint is unreachable");
            return;
        }

        print("Synchronizing service networks...");
        print(NET_FORMAT, "ID", "Name", "Type", "VNI", "Subnet", "Service IP");
        OSClient osClient = OSFactory.clientFromAccess(osAccess);
        osClient.networking().network().list().forEach(osNet -> {
            ServiceNetwork snet = DefaultServiceNetwork.builder()
                    .id(NetworkId.of(osNet.getId()))
                    .name(osNet.getName())
                    .type(ServiceNetwork.NetworkType.PRIVATE)
                    .segmentId(SegmentId.of(Long.valueOf(osNet.getProviderSegID())))
                    .build();
            try {
                if (snetService.serviceNetwork(snet.id()) != null) {
                    snetService.updateServiceNetwork(snet);
                } else {
                    snetService.createServiceNetwork(snet);
                }
            } catch (Exception ignore) {
            }
        });

        osClient.networking().subnet().list().forEach(osSubnet -> {
            try {
                ServiceNetwork snet = DefaultServiceNetwork.builder()
                        .id(NetworkId.of(osSubnet.getNetworkId()))
                        .subnet(IpPrefix.valueOf(osSubnet.getCidr()))
                        .serviceIp(IpAddress.valueOf(osSubnet.getGateway()))
                        .build();
                snetService.updateServiceNetwork(snet);
                ServiceNetwork updated = snetService.serviceNetwork(snet.id());
                print(NET_FORMAT, updated.id(),
                      updated.name(),
                      updated.type(),
                      updated.segmentId(),
                      updated.subnet(),
                      updated.serviceIp());
            } catch (Exception e) {
                print(e.getMessage());
            }
        });

        print("\nSynchronizing service ports...");
        print(PORT_FORMAT, "ID", "Name", "MAC", "IP", "VLAN", "WAN IPs");
        osClient.networking().port().list().forEach(osPort -> {
            ServicePort.Builder sportBuilder = DefaultServicePort.builder()
                    .id(PortId.of(osPort.getId()))
                    .name(PORT_NAME_PREFIX + osPort.getId().substring(0, 11))
                    .networkId(NetworkId.of(osPort.getNetworkId()));

            if (osPort.getMacAddress() != null) {
                sportBuilder.mac(MacAddress.valueOf(osPort.getMacAddress()));
            }
            if (!osPort.getFixedIps().isEmpty()) {
                sportBuilder.ip(IpAddress.valueOf(
                        osPort.getFixedIps().iterator().next().getIpAddress()));
            }
            ServicePort sport = sportBuilder.build();
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
                    updated.name(),
                    updated.mac(),
                    updated.ip(),
                    updated.vlanId() != null ? updated.vlanId() : "",
                    floatingIps.isEmpty() ? "" : floatingIps);
        });
    }
}
