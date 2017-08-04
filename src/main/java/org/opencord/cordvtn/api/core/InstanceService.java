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
package org.opencord.cordvtn.api.core;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostDescription;

/**
 * Provides service instance addition or removal.
 */
public interface InstanceService {

    // TODO add get instance

    /**
     * Adds a service instance on a given connect point. Or updates if the
     * instance already exists.
     *
     * @param connectPoint connect point of the instance
     */
    void addInstance(ConnectPoint connectPoint);

    /**
     * Adds a host with a given host ID and host description.
     *
     * @param hostId      host id
     * @param description host description
     */
    void addInstance(HostId hostId, HostDescription description);

    /**
     * Removes a service instance from a given connect point.
     *
     * @param connectPoint connect point
     */
    void removeInstance(ConnectPoint connectPoint);

    /**
     * Removes host with host ID.
     *
     * @param hostId host id
     */
    void removeInstance(HostId hostId);
}
