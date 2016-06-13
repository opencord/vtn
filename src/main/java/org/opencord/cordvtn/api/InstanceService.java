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

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostDescription;

/**
 * Provides service instance addition or removal.
 */
public interface InstanceService {

    /**
     * Adds a service instance on a given connect point.
     *
     * @param connectPoint connect point of the instance
     */
    void addInstance(ConnectPoint connectPoint);

    /**
     * Removes a service instance from a given connect point.
     *
     * @param connectPoint connect point
     */
    void removeInstance(ConnectPoint connectPoint);

    /**
     * Adds a nested instance with given host ID and host description.
     * Nested instance can be a container inside a virtual machine, for example.
     * DHCP is not supported for the nested instance.
     *
     * @param hostId host id
     * @param description host description
     */
    void addNestedInstance(HostId hostId, HostDescription description);

    /**
     * Removes nested instance with a given host ID.
     *
     * @param hostId host id
     */
    void removeNestedInstance(HostId hostId);
}
