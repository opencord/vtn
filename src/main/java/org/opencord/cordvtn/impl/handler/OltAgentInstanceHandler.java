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
package org.opencord.cordvtn.impl.handler;

import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;

import org.opencord.cordvtn.impl.AbstractInstanceHandler;
import org.opencord.cordvtn.api.CordVtnConfig;
import org.opencord.cordvtn.api.Instance;
import org.opencord.cordvtn.api.InstanceHandler;
import org.onosproject.net.DeviceId;

import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.basics.SubjectFactories;
import org.opencord.cordconfig.access.AccessAgentConfig;
import org.opencord.cordconfig.access.AccessAgentData;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.onosproject.xosclient.api.VtnServiceApi.ServiceType.OLT_AGENT;

/**
 * Provides network connectivity for OLT agent instances.
 */
@Component(immediate = true)
public class OltAgentInstanceHandler extends AbstractInstanceHandler implements InstanceHandler {

    private static final Class<AccessAgentConfig> CONFIG_CLASS = AccessAgentConfig.class;
    private ConfigFactory<DeviceId, AccessAgentConfig> configFactory =
            new ConfigFactory<DeviceId, AccessAgentConfig>(
                    SubjectFactories.DEVICE_SUBJECT_FACTORY, CONFIG_CLASS, "accessAgent") {
                @Override
                public AccessAgentConfig createConfig() {
                    return new AccessAgentConfig();
                }
            };

    private Map<DeviceId, AccessAgentData> oltAgentData = Maps.newConcurrentMap();

    @Activate
    protected void activate() {
        serviceType = Optional.of(OLT_AGENT);
        configListener = new InternalConfigListener();
        super.activate();

        configRegistry.registerConfigFactory(configFactory);
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void instanceDetected(Instance instance) {
        log.info("OLT agent instance detected {}", instance);
        // TODO implement
    }

    @Override
    public void instanceRemoved(Instance instance) {
        log.info("OLT agent instance removed {}", instance);
        // TODO implement
    }

    private void readAccessAgentConfig() {

        Set<DeviceId> deviceSubjects = configRegistry.getSubjects(DeviceId.class, CONFIG_CLASS);
        deviceSubjects.stream().forEach(subject -> {
            AccessAgentConfig config = configRegistry.getConfig(subject, CONFIG_CLASS);
            if (config != null) {
                oltAgentData.put(subject, config.getAgent());
            }
        });
    }

    private class InternalConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {

            switch (event.type()) {
                case CONFIG_UPDATED:
                case CONFIG_ADDED:
                    if (event.configClass().equals(CordVtnConfig.class)) {
                        readConfiguration();
                    } else if (event.configClass().equals(CONFIG_CLASS)) {
                        readAccessAgentConfig();
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
