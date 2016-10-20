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

import com.jcraft.jsch.Session;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.impl.CordVtnNodeManager;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceService;

import java.util.Set;

import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.opencord.cordvtn.api.Constants.*;
import static org.opencord.cordvtn.impl.RemoteIpCommandUtil.*;

/**
 * Checks detailed node init state.
 */
@Command(scope = "onos", name = "cordvtn-node-check",
        description = "Shows detailed node init state")
public class CordVtnNodeCheckCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "hostname", description = "Hostname",
            required = true, multiValued = false)
    private String hostname = null;

    @Override
    protected void execute() {
        CordVtnNodeManager nodeManager = AbstractShellCommand.get(CordVtnNodeManager.class);
        DeviceService deviceService = AbstractShellCommand.get(DeviceService.class);

        CordVtnNode node = nodeManager.getNodes()
                .stream()
                .filter(n -> n.hostname().equals(hostname))
                .findFirst()
                .orElse(null);

        if (node == null) {
            print("Cannot find %s from registered nodes", hostname);
            return;
        }

        print("%n[Integration Bridge Status]");
        Device device = deviceService.getDevice(node.integrationBridgeId());
        if (device != null) {
            print("%s %s=%s available=%s %s",
                  deviceService.isAvailable(device.id()) ? MSG_OK : MSG_NO,
                  INTEGRATION_BRIDGE,
                  device.id(),
                  deviceService.isAvailable(device.id()),
                  device.annotations());

            node.systemIfaces().stream().forEach(iface -> print(
                    getPortState(deviceService, node.integrationBridgeId(), iface)));
        } else {
            print("%s %s=%s is not available",
                  MSG_NO,
                  INTEGRATION_BRIDGE,
                  node.integrationBridgeId());
        }

        print("%n[Interfaces and IP setup]");
        Session session = connect(node.sshInfo());
        if (session != null) {
            Set<IpAddress> ips = getCurrentIps(session, INTEGRATION_BRIDGE);
            boolean isUp = isInterfaceUp(session, INTEGRATION_BRIDGE);
            boolean isIp = ips.contains(node.dataIp().ip()) && ips.contains(node.localMgmtIp().ip());

            print("%s %s up=%s Ips=%s",
                  isUp && isIp ? MSG_OK : MSG_NO,
                  INTEGRATION_BRIDGE,
                  isUp ? Boolean.TRUE : Boolean.FALSE,
                  getCurrentIps(session, INTEGRATION_BRIDGE));

            print(getSystemIfaceState(session, node.dataIface()));
            if (node.hostMgmtIface().isPresent()) {
                print(getSystemIfaceState(session, node.hostMgmtIface().get()));
            }

            disconnect(session);
        } else {
            print("%s Unable to SSH to %s", MSG_NO, node.hostname());
        }
    }

    private String getPortState(DeviceService deviceService, DeviceId deviceId, String portName) {
        Port port = deviceService.getPorts(deviceId).stream()
                .filter(p -> p.annotations().value(PORT_NAME).equals(portName) &&
                        p.isEnabled())
                .findAny().orElse(null);

        if (port != null) {
            return String.format("%s %s portNum=%s enabled=%s %s",
                                 port.isEnabled() ? MSG_OK : MSG_NO,
                                 portName,
                                 port.number(),
                                 port.isEnabled() ? Boolean.TRUE : Boolean.FALSE,
                                 port.annotations());
        } else {
            return String.format("%s %s does not exist", MSG_NO, portName);
        }
    }

    private String getSystemIfaceState(Session session, String iface) {
        boolean isUp = isInterfaceUp(session, iface);
        boolean isIp = getCurrentIps(session, iface).isEmpty();
        return String.format("%s %s up=%s IpFlushed=%s",
              isUp && isIp ? MSG_OK : MSG_NO,
              iface,
              isUp ? Boolean.TRUE : Boolean.FALSE,
              isIp ? Boolean.TRUE : Boolean.FALSE);
    }
}
