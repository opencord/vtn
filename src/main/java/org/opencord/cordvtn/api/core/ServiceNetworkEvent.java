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
package org.opencord.cordvtn.api.core;

import org.joda.time.LocalDateTime;
import org.onosproject.event.AbstractEvent;
import org.opencord.cordvtn.api.net.Provider;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * Describes service network event.
 */
public class ServiceNetworkEvent extends AbstractEvent<ServiceNetworkEvent.Type, ServiceNetwork> {

    private final ServicePort servicePort;
    private final Provider provider;

    /**
     * Type of service network event.
     */
    public enum Type {
        /**
         * Signifies that a new service network has been created.
         */
        SERVICE_NETWORK_CREATED,

        /**
         * Signifies that some service network attributes have changed.
         */
        SERVICE_NETWORK_UPDATED,

        /**
         * Signifies that provider network was added.
         */
        SERVICE_NETWORK_PROVIDER_ADDED,

        /**
         * Signifies that provider network was removed.
         */
        SERVICE_NETWORK_PROVIDER_REMOVED,

        /**
         * Signifies that a service network has been removed.
         */
        SERVICE_NETWORK_REMOVED,

        /**
         * Signifies that a new service port has been created.
         */
        SERVICE_PORT_CREATED,

        /**
         * Signifies that some service port attributes have changed.
         */
        SERVICE_PORT_UPDATED,

        /**
         * Signifies that a service port has been removed.
         */
        SERVICE_PORT_REMOVED
    }

    /**
     * Creates an event of a given type and for the specified service network and
     * the current time.
     *
     * @param type           service network event type
     * @param serviceNetwork service network subject
     */
    public ServiceNetworkEvent(Type type, ServiceNetwork serviceNetwork) {
        super(type, serviceNetwork);
        this.servicePort = null;
        this.provider = null;
    }

    /**
     * Creates an event of a given type and for the specified service network,
     * port and the current time.
     *
     * @param type            service network event type
     * @param serviceNetwork  service network subject
     * @param servicePort     optional service port subject
     */
    public ServiceNetworkEvent(Type type, ServiceNetwork serviceNetwork, ServicePort servicePort) {
        super(type, serviceNetwork);
        this.servicePort = servicePort;
        this.provider = null;
    }

    /**
     * Creates an event of a given type and for the specified service network,
     * provider, dependency type with the provider, and the current time.
     *
     * @param type           service network event type
     * @param serviceNetwork service network subject
     * @param provider       optional provider network
     */
    public ServiceNetworkEvent(Type type, ServiceNetwork serviceNetwork, Provider provider) {
        super(type, serviceNetwork);
        this.servicePort = null;
        this.provider = provider;
    }

    /**
     * Returns the service port subject.
     * It returns valid value only with the service port events.
     *
     * @return service port; null if the event is not service port specific
     */
    public ServicePort servicePort() {
        return servicePort;
    }

    /**
     * Returns the provider of the service network.
     *
     * @return provider network; null if the event is not provider specific
     */
    public Provider provider() {
        return provider;
    }

    @Override
    public String toString() {
        if (servicePort == null) {
            return super.toString();
        }
        return toStringHelper(this)
                .add("time", new LocalDateTime(time()))
                .add("type", type())
                .add("serviceNetwork", subject())
                .add("servicePort", servicePort)
                .add("provider", provider)
                .toString();
    }
}
