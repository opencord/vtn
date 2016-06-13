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

import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Host;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.xosclient.api.OpenStackAccess;
import org.onosproject.xosclient.api.VtnPort;
import org.onosproject.xosclient.api.VtnPortApi;
import org.onosproject.xosclient.api.VtnPortId;
import org.onosproject.xosclient.api.VtnService;
import org.onosproject.xosclient.api.VtnServiceApi;
import org.onosproject.xosclient.api.VtnServiceApi.ServiceType;
import org.onosproject.xosclient.api.VtnServiceId;
import org.onosproject.xosclient.api.XosAccess;
import org.onosproject.xosclient.api.XosClientService;
import org.opencord.cordvtn.api.Constants;
import org.opencord.cordvtn.api.CordVtnConfig;
import org.opencord.cordvtn.api.Instance;
import org.opencord.cordvtn.api.InstanceHandler;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.cordvtn.api.Constants.ERROR_OPENSTACK_ACCESS;
import static org.opencord.cordvtn.api.Constants.ERROR_XOS_ACCESS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides default virtual network connectivity for service instances.
 */
public abstract class AbstractInstanceHandler implements InstanceHandler {

    protected final Logger log = getLogger(getClass());

    protected CoreService coreService;
    protected MastershipService mastershipService;
    protected NetworkConfigRegistry configRegistry;
    protected HostService hostService;
    protected XosClientService xosClient;

    protected ApplicationId appId;
    protected Optional<ServiceType> serviceType = Optional.empty();
    protected NetworkConfigListener configListener = new InternalConfigListener();
    protected HostListener hostListener = new InternalHostListener();

    private XosAccess xosAccess = null;
    private OpenStackAccess osAccess = null;
    private final ExecutorService eventExecutor = newSingleThreadScheduledExecutor(
            groupedThreads(this.getClass().getSimpleName(), "event-handler"));

    protected void activate() {
        ServiceDirectory services = new DefaultServiceDirectory();
        coreService = services.get(CoreService.class);
        configRegistry = services.get(NetworkConfigRegistry.class);
        mastershipService = services.get(MastershipService.class);
        hostService = services.get(HostService.class);
        xosClient = services.get(XosClientService.class);

        appId = coreService.registerApplication(Constants.CORDVTN_APP_ID);
        hostService.addListener(hostListener);
        configRegistry.addListener(configListener);

        log.info("Started");
    }

    protected void deactivate() {
        hostService.removeListener(hostListener);
        configRegistry.removeListener(configListener);
        eventExecutor.shutdown();

        log.info("Stopped");
    }

    protected VtnService getVtnService(VtnServiceId serviceId) {
        checkNotNull(osAccess, ERROR_OPENSTACK_ACCESS);
        checkNotNull(xosAccess, ERROR_XOS_ACCESS);

        // TODO remove openstack access when XOS provides all information
        VtnServiceApi serviceApi = xosClient.getClient(xosAccess).vtnService();
        VtnService service = serviceApi.service(serviceId, osAccess);
        if (service == null) {
            log.warn("Failed to get VtnService for {}", serviceId);
        }
        return service;
    }

    protected VtnPort getVtnPort(Instance instance) {
        checkNotNull(osAccess, ERROR_OPENSTACK_ACCESS);
        checkNotNull(xosAccess, ERROR_XOS_ACCESS);

        VtnPortId vtnPortId = instance.portId();
        VtnPortApi portApi = xosClient.getClient(xosAccess).vtnPort();
        VtnPort vtnPort = portApi.vtnPort(vtnPortId, osAccess);
        if (vtnPort == null) {
            log.warn("Failed to get port information of {}", instance);
            return null;
        }
        return vtnPort;
    }

    protected Set<Instance> getInstances(VtnServiceId serviceId) {
        return StreamSupport.stream(hostService.getHosts().spliterator(), false)
                .filter(host -> Objects.equals(
                        serviceId.id(),
                        host.annotations().value(Instance.SERVICE_ID)))
                .map(Instance::of)
                .collect(Collectors.toSet());
    }

    protected void readConfiguration() {
        CordVtnConfig config = configRegistry.getConfig(appId, CordVtnConfig.class);
        if (config == null) {
            log.debug("No configuration found");
            return;
        }
        osAccess = config.openstackAccess();
        xosAccess = config.xosAccess();
    }

    private class InternalHostListener implements HostListener {

        @Override
        public void event(HostEvent event) {
            Host host = event.subject();
            if (!mastershipService.isLocalMaster(host.location().deviceId())) {
                // do not allow to proceed without mastership
                return;
            }

            Instance instance = Instance.of(host);
            if (serviceType.isPresent() &&
                    !serviceType.get().equals(instance.serviceType())) {
                // not my service instance, do nothing
                return;
            }

            switch (event.type()) {
                case HOST_UPDATED:
                case HOST_ADDED:
                    eventExecutor.execute(() -> instanceDetected(instance));
                    break;
                case HOST_REMOVED:
                    eventExecutor.execute(() -> instanceRemoved(instance));
                    break;
                default:
                    break;
            }
        }
    }

    private class InternalConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            if (!event.configClass().equals(CordVtnConfig.class)) {
                return;
            }

            switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                    readConfiguration();
                    break;
                default:
                    break;
            }
        }
    }
}
