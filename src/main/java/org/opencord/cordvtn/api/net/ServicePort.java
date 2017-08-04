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
package org.opencord.cordvtn.api.net;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;

import java.util.Set;

/**
 * Representation of a service port.
 */
public interface ServicePort {

    /**
     * Returns the port identifier.
     *
     * @return port id
     */
    PortId id();

    /**
     * Returns the port name on a switch.
     * This name is used as key for identifying a service port when a port is
     * added or updated.
     *
     * @return port name
     */
    String name();

    /**
     * Returns associated network identifier of the service port.
     *
     * @return network id
     */
    NetworkId networkId();

    /**
     * Returns the MAC address of the service port.
     *
     * @return mac address
     */
    MacAddress mac();

    /**
     * Returns the fixed IP address of the service port.
     *
     * @return ip address
     */
    IpAddress ip();

    /**
     * Returns VLAN of service the port.
     *
     * @return vlan id
     */
    VlanId vlanId();

    /**
     * Returns additional floating address pairs of the service port.
     *
     * @return set of mac and ip address pair
     */
    Set<AddressPair> addressPairs();

    /**
     * Builder of new service port entities.
     */
    interface Builder {

        /**
         * Builds an immutable service port instance.
         *
         * @return service port
         */
        ServicePort build();

        /**
         * Returns service port builder with the supplied identifier.
         *
         * @param id port id
         * @return service port builder
         */
        Builder id(PortId id);

        /**
         * Returns service port builder with the supplied name.
         *
         * @param name port name
         * @return service port builder
         */
        Builder name(String name);

        /**
         * Returns service port builder with the supplied network identifier.
         *
         * @param networkId network id
         * @return service port builder
         */
        Builder networkId(NetworkId networkId);

        /**
         * Returns service port builder with the supplied MAC address.
         *
         * @param mac mac address
         * @return service port builder
         */
        Builder mac(MacAddress mac);

        /**
         * Returns service port builder with the supplied IP address.
         *
         * @param ip ip address
         * @return service port builder
         */
        Builder ip(IpAddress ip);

        /**
         * Returns service port builder with the supplied VLAN.
         *
         * @param vlanId vlan id
         * @return service port builder
         */
        Builder vlanId(VlanId vlanId);

        /**
         * Returns service port builder with the supplied address pairs.
         *
         * @param addressPairs set of address pair
         * @return service port builder
         */
        Builder addressPairs(Set<AddressPair> addressPairs);
    }
}
