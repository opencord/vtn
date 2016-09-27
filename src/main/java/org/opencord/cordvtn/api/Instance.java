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
package org.opencord.cordvtn.api;

import com.google.common.base.Strings;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.opencord.cordvtn.api.ServiceNetwork.ServiceNetworkType;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides methods to help to handle network network instance.
 */
public final class Instance {

    public static final String NETWORK_ID = "networkId";
    public static final String NETWORK_TYPE = "networkType";
    public static final String PORT_ID = "portId";
    public static final String CREATE_TIME = "createTime";
    public static final String NESTED_INSTANCE = "nestedInstance";
    public static final String TRUE = "true";

    private final Host host;

    /**
     * Default constructor.
     *
     * @param instance host object of this instance
     */
    private Instance(Host instance) {
        this.host = instance;
    }

    /**
     * Returns host object of this instance.
     *
     * @return host
     */
    public Host host() {
        return this.host;
    }

    /**
     * Returns new instance.
     *
     * @param host host object of this instance
     * @return instance
     */
    public static Instance of(Host host) {
        checkNotNull(host);
        checkArgument(!Strings.isNullOrEmpty(host.annotations().value(NETWORK_ID)));
        checkArgument(!Strings.isNullOrEmpty(host.annotations().value(NETWORK_TYPE)));
        checkArgument(!Strings.isNullOrEmpty(host.annotations().value(PORT_ID)));
        checkArgument(!Strings.isNullOrEmpty(host.annotations().value(CREATE_TIME)));

        return new Instance(host);
    }

    /**
     * Returns network ID of a given host.
     *
     * @return network id
     */
    public NetworkId netId() {
        String netId = host.annotations().value(NETWORK_ID);
        return NetworkId.of(netId);
    }

    /**
     * Returns network type of a given host.
     *
     * @return network type
     */
    public ServiceNetworkType netType() {
        String netType = host.annotations().value(NETWORK_TYPE);
        return ServiceNetworkType.valueOf(netType);
    }

    /**
     * Returns port ID of a given host.
     *
     * @return port id
     */
    public PortId portId() {
        String portId = host.annotations().value(PORT_ID);
        return PortId.of(portId);
    }

    /**
     * Returns if the instance is nested container or not.
     *
     * @return true if it's nested container; false otherwise
     */
    public boolean isNestedInstance() {
        return host.annotations().value(NESTED_INSTANCE) != null;
    }

    /**
     * Returns MAC address of this instance.
     *
     * @return mac address
     */
    public MacAddress mac() {
        return host.mac();
    }

    /**
     * Returns IP address of this instance.
     *
     * @return ip address
     */
    public Ip4Address ipAddress() {
        // assume all instance has only one IP address, and only IP4 is supported now
        return host.ipAddresses().stream().findFirst().get().getIp4Address();
    }

    /**
     * Returns device ID of this host.
     *
     * @return device id
     */
    public DeviceId deviceId() {
        return host.location().deviceId();
    }

    /**
     * Returns the port number where this host is.
     *
     * @return port number
     */
    public PortNumber portNumber() {
        return host.location().port();
    }

    /**
     * Returns annotation value with a given key.
     *
     * @param annotationKey annotation key
     * @return annotation value
     */
    public String getAnnotation(String annotationKey) {
        return host.annotations().value(annotationKey);
    }

    @Override
    public String toString() {
        return host.toString();
    }
}
