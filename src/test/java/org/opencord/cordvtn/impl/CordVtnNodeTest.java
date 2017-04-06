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
package org.opencord.cordvtn.impl;

import org.onlab.packet.ChassisId;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.DefaultPort;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.provider.ProviderId;
import org.opencord.cordvtn.api.net.CidrAddr;
import org.opencord.cordvtn.api.node.CordVtnNode;
import org.opencord.cordvtn.api.node.CordVtnNodeState;
import org.opencord.cordvtn.api.node.SshAccessInfo;

import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.onosproject.net.Device.Type.SWITCH;

/**
 * Provides a set of test CordVtnNode parameters for use with CordVtnNode related tests.
 */
public abstract class CordVtnNodeTest {

    public static final SshAccessInfo TEST_SSH_INFO = new SshAccessInfo(
            Ip4Address.valueOf("192.168.0.1"),
            TpPort.tpPort(22),
            "root",
            "/root/.ssh/id_rsa");
    public static final CidrAddr TEST_CIDR_ADDR = CidrAddr.valueOf("192.168.0.1/24");
    public static final String TEST_DATA_IFACE = "eth0";
    public static final String TEST_VXLAN_IFACE = "vxlan";

    public static Device createDevice(long devIdNum) {
        return new DefaultDevice(new ProviderId("of", "foo"),
                DeviceId.deviceId(String.format("of:%016d", devIdNum)),
                SWITCH,
                "manufacturer",
                "hwVersion",
                "swVersion",
                "serialNumber",
                new ChassisId(1));
    }

    public static Port createPort(Device device, long portNumber, String portName) {
        return new DefaultPort(device,
                PortNumber.portNumber(portNumber),
                true,
                DefaultAnnotations.builder().set(PORT_NAME, portName).build());
    }

    public static CordVtnNode createNode(String hostname, Device device, CordVtnNodeState state) {
        return DefaultCordVtnNode.builder()
                .hostname(hostname)
                .hostManagementIp(TEST_CIDR_ADDR)
                .localManagementIp(TEST_CIDR_ADDR)
                .dataIp(TEST_CIDR_ADDR)
                .integrationBridgeId(device.id())
                .dataInterface(TEST_DATA_IFACE)
                .sshInfo(TEST_SSH_INFO)
                .state(state)
                .build();
    }
}
