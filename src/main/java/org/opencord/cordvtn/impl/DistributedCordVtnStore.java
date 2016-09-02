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
package org.opencord.cordvtn.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.opencord.cordvtn.api.CordVtnStore;
import org.opencord.cordvtn.api.NetworkId;
import org.opencord.cordvtn.api.PortId;
import org.opencord.cordvtn.api.ServiceNetwork;
import org.opencord.cordvtn.api.ServicePort;
import org.slf4j.Logger;

import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of the cordvtn service.
 */
@Component(immediate = true)
@Service
public class DistributedCordVtnStore implements CordVtnStore {
    protected final Logger log = getLogger(getClass());

    private static final String MSG_SERVICE_NET  = "Service network %s %s";
    private static final String MSG_SERVICE_PORT = "Service port %s %s";
    private static final String CREATED = "created";
    private static final String UPDATED = "updated";
    private static final String REMOVED = "removed";

    @Activate
    protected void activate() {
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void createServiceNetwork(ServiceNetwork serviceNet) {
        // TODO implement
        log.info(String.format(MSG_SERVICE_NET, CREATED, serviceNet));
    }

    @Override
    public void updateServiceNetwork(ServiceNetwork serviceNet) {
        // TODO implement
        log.info(String.format(MSG_SERVICE_NET, UPDATED, serviceNet));
    }

    @Override
    public ServiceNetwork getServiceNetwork(NetworkId netId) {
        // TODO implement
        return null;
    }

    @Override
    public Set<ServiceNetwork> getServiceNetworks() {
        // TODO implement
        return null;
    }

    @Override
    public void removeServiceNetwork(NetworkId netId) {
        // TODO implement
        log.info(String.format(MSG_SERVICE_NET, REMOVED, netId));
    }

    @Override
    public void createServicePort(ServicePort servicePort) {
        // TODO implement
        log.info(String.format(MSG_SERVICE_PORT, CREATED, servicePort));
    }

    @Override
    public ServicePort getServicePort(PortId portId) {
        // TODO implement
        return null;
    }

    @Override
    public Set<ServicePort> getServicePorts() {
        // TODO implement
        return null;
    }

    @Override
    public void removeServicePort(PortId portId) {
        // TODO implement
        log.info(String.format(MSG_SERVICE_PORT, REMOVED, portId));
    }
}
