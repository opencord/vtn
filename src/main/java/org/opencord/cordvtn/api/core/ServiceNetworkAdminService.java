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
package org.opencord.cordvtn.api.core;

import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;

/**
 * Service for administering the inventory of {@link ServiceNetwork}.
 */
public interface ServiceNetworkAdminService extends ServiceNetworkService {

    /**
     * Purges internal network states.
     */
    void purgeStates();

    /**
     * Request state synchronization from XOS. The XOS connection parameters
     * will be pulled from the netcfg.
     */
    void syncXosState();

    /**
     * Request state synchronization from XOS using the given XOS connection
     * parameters.
     *
     * @param endpoint XOS REST endpoint
     * @param user XOS username
     * @param password XOS password
     */
    void syncXosState(String endpoint, String user, String password);

    /**
     * Synchronize state with Neutron. The Neutron connection parameters will be
     * pulled from the netcfg.
     */
    void syncNeutronState();

    /**
     * Synchronize state with Neutron using the given Neutron connection
     * parameters.
     *
     * @param endpoint Neutron REST endpoint
     * @param tenant Neutron tenant
     * @param user Neutron username
     * @param password Neutron password
     */
    void syncNeutronState(String endpoint, String tenant, String user, String password);

    /**
     * Creates a service network with the given information.
     *
     * @param serviceNetwork the new service network
     */
    void createServiceNetwork(ServiceNetwork serviceNetwork);

    /**
     * Updates a service network with the given information.
     *
     * @param serviceNetwork the updated service network
     */
    void updateServiceNetwork(ServiceNetwork serviceNetwork);

    /**
     * Removes a service network with the given network id.
     *
     * @param networkId network id
     */
    void removeServiceNetwork(NetworkId networkId);

    /**
     * Creates a service port with the given information.
     *
     * @param servicePort the new service port
     */
    void createServicePort(ServicePort servicePort);

    /**
     * Updates a service port with the given information.
     *
     * @param servicePort the updated service port
     */
    void updateServicePort(ServicePort servicePort);

    /**
     * Removes a service port with the given port id.
     *
     * @param portId port id
     */
    void removeServicePort(PortId portId);
}
