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
package org.opencord.cordvtn.impl.external;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.NetworkService;
import org.opencord.cordvtn.api.net.PortId;
import org.opencord.cordvtn.api.net.SubnetId;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.identity.Access;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Implementation of {@link NetworkService} with OpenStack networking service.
 */
public final class OpenStackNetworking implements NetworkService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String ERR_AUTH = "OpenStack authentication failure";

    private final String endpoint;
    private final String tenant;
    private final String user;
    private final String password;
    private final Access osAcess;

    private OpenStackNetworking(String endpoint,
                                String tenant,
                                String user,
                                String password) {
        this.endpoint = endpoint;
        this.tenant = tenant;
        this.user = user;
        this.password = password;

        try {
            this.osAcess = OSFactory.builder()
                    .endpoint(this.endpoint)
                    .tenantName(this.tenant)
                    .credentials(this.user, this.password)
                    .authenticate()
                    .getAccess();
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException(ERR_AUTH);
        }
    }

    @Override
    public Network network(NetworkId netId) {
        OSClient osClient = OSFactory.clientFromAccess(osAcess);
        return osClient.networking().network().get(netId.id());
    }

    @Override
    public Set<Network> networks() {
        OSClient osClient = OSFactory.clientFromAccess(osAcess);
        return ImmutableSet.copyOf(osClient.networking().network().list());
    }

    @Override
    public Port port(PortId portId) {
        OSClient osClient = OSFactory.clientFromAccess(osAcess);
        return osClient.networking().port().get(portId.id());
    }

    @Override
    public Set<Port> ports() {
        OSClient osClient = OSFactory.clientFromAccess(osAcess);
        return ImmutableSet.copyOf(osClient.networking().port().list());
    }

    @Override
    public Subnet subnet(SubnetId subnetId) {
        OSClient osClient = OSFactory.clientFromAccess(osAcess);
        return osClient.networking().subnet().get(subnetId.id());
    }

    @Override
    public Set<Subnet> subnets() {
        OSClient osClient = OSFactory.clientFromAccess(osAcess);
        return ImmutableSet.copyOf(osClient.networking().subnet().list());
    }

    /**
     * Returns endpoint url for the OpenStack networking API access.
     *
     * @return endpoint url as a string
     */
    public String endpoint() {
        return endpoint;
    }

    /**
     * Returns tenant for the OpenStack networking API access.
     *
     * @return tenant name as a string
     */
    public String tenant() {
        return tenant;
    }

    /**
     * Returns user name for the OpenStack networking API access.
     *
     * @return user name as a string
     */
    public String user() {
        return this.user;
    }

    /**
     * Returns password for the OpenStack networking API access.
     *
     * @return password as a string
     */
    public String password() {
        return this.password;
    }

    /**
     * Returns new OpenStack networking builder instance.
     * @return openstack networking builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of the OpenStack networking entities.
     */
    public static final class Builder {

        private String endpoint;
        private String tenant;
        private String user;
        private String password;

        private Builder() {
        }

        /**
         * Builds immutable OpenStack networking instance.
         *
         * @return openstack networking instance
         */
        public OpenStackNetworking build() {
            checkArgument(!Strings.isNullOrEmpty(endpoint));
            checkArgument(!Strings.isNullOrEmpty(tenant));
            checkArgument(!Strings.isNullOrEmpty(user));
            checkArgument(!Strings.isNullOrEmpty(password));

            return new OpenStackNetworking(endpoint, tenant, user, password);
        }

        /**
         * Returns OpenStack networking builder with the supplied endpoint.
         *
         * @param endpoint endpoint url as a string
         * @return openstack networking builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Returns OpenStack networking builder with the supplied tenant name.
         *
         * @param tenant tenant name as a string
         * @return openstack networking builder
         */
        public Builder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        /**
         * Returns OpenStack networking builder with the supplied user name.
         *
         * @param user user name as a string
         * @return openstack networking builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Returns OpenStack networking builder with the supplied password.
         *
         * @param password password as a string
         * @return openstack networking builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }
    }
}
