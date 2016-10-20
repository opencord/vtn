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
package org.opencord.cordvtn.api.net;

import com.google.common.base.MoreObjects;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Representation of the IP and MAC address pair.
 */
public final class AddressPair {

    private final IpAddress ip;
    private final MacAddress mac;

    private AddressPair(IpAddress ip, MacAddress mac) {
        this.ip = ip;
        this.mac = mac;
    }

    /**
     * Returns the IP address.
     *
     * @return ip address
     */
    public IpAddress ip() {
        return ip;
    }

    /**
     * Returns the MAC address.
     *
     * @return mac address
     */
    public MacAddress mac() {
        return mac;
    }

    /**
     * Returns an address pair instance with the given ip and mac address.
     *
     * @param ip ip address
     * @param mac mac address
     * @return address pair
     */
    public static AddressPair of(IpAddress ip, MacAddress mac) {
        checkNotNull(ip, "AddressPair IP cannot be null");
        checkNotNull(mac, "AddressPair MAC cannot be null");

        return new AddressPair(ip, mac);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof AddressPair) {
            AddressPair that = (AddressPair) obj;
            if (Objects.equals(ip, that.ip) && Objects.equals(mac, that.mac)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, mac);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("ip", ip)
                .add("mac", mac)
                .toString();
    }
}
