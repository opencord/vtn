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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import org.glassfish.jersey.client.ClientProperties;
import org.onlab.util.Tools;
import org.onosproject.rest.AbstractWebResource;
import org.opencord.cordvtn.api.NetworkId;
import org.opencord.cordvtn.api.PortId;
import org.opencord.cordvtn.api.ServiceNetwork;
import org.opencord.cordvtn.api.ServicePort;
import org.opencord.cordvtn.api.VtnClientService;
import org.slf4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of VtnClientService for XOS.
 */
public final class XosVtnClient extends AbstractWebResource implements VtnClientService {

    protected final Logger log = getLogger(getClass());

    private static final String URL_BASE = "/api/service/vtn/";
    private static final String URL_SERVICE_NETWORKS = "serviceNetworks/";
    private static final String URL_SERVICE_PORTS = "servicePorts/";

    private static final String SERVICE_PORTS = "servicePorts";
    private static final String SERVICE_PORT  = "servicePort";
    private static final String SERVICE_NETWORKS = "serviceNetworks";
    private static final String SERVICE_NETWORK  = "serviceNetwork";
    private static final String EMPTY_JSON_STRING = "{}";

    private static final String MSG_RECEIVED = "Received ";
    private static final String ERR_LOG = "Received %s result with wrong format: %s";

    private static final int DEFAULT_TIMEOUT_MS = 2000;

    private final String endpoint;
    private final String user;
    private final String password;

    private final Client client = ClientBuilder.newClient();

    private XosVtnClient(String endpoint, String user, String password) {
        this.endpoint = endpoint;
        this.user = user;
        this.password = password;

        client.property(ClientProperties.CONNECT_TIMEOUT, DEFAULT_TIMEOUT_MS);
        client.property(ClientProperties.READ_TIMEOUT, DEFAULT_TIMEOUT_MS);
        mapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Set<ServiceNetwork> serviceNetworks() {
        String response = restGet(URL_SERVICE_NETWORKS);
        final String error = String.format(ERR_LOG, SERVICE_NETWORKS, response);
        try {
            JsonNode jsonTree = mapper().readTree(response).get(SERVICE_NETWORKS);
            if (jsonTree == null) {
                throw new IllegalArgumentException(error);
            }
            log.trace(MSG_RECEIVED + SERVICE_NETWORKS);
            log.trace(mapper().writeValueAsString(jsonTree));
            return Tools.stream(jsonTree).map(snet -> codec(ServiceNetwork.class)
                    .decode((ObjectNode) snet, this))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new IllegalArgumentException(error);
        }
    }

    @Override
    public ServiceNetwork serviceNetwork(NetworkId netId) {
        String response = restGet(URL_SERVICE_NETWORKS + netId.id());
        final String error = String.format(ERR_LOG, SERVICE_NETWORK, response);
        try {
            JsonNode jsonTree = mapper().readTree(response).get(SERVICE_NETWORK);
            if (jsonTree == null) {
                throw new IllegalArgumentException(error);
            }
            log.trace(MSG_RECEIVED + SERVICE_NETWORK);
            log.trace(mapper().writeValueAsString(jsonTree));
            return codec(ServiceNetwork.class).decode((ObjectNode) jsonTree, this);
        } catch (IOException e) {
            throw new IllegalArgumentException(error);
        }
    }

    @Override
    public Set<ServicePort> servicePorts() {
        String response = restGet(URL_SERVICE_PORTS);
        final String error = String.format(ERR_LOG, SERVICE_PORTS, response);
        try {
            JsonNode jsonTree = mapper().readTree(response).get(SERVICE_PORTS);
            if (jsonTree == null) {
                throw new IllegalArgumentException(error);
            }
            log.trace(MSG_RECEIVED + SERVICE_PORTS);
            log.trace(mapper().writeValueAsString(jsonTree));
            return Tools.stream(jsonTree).map(sport -> codec(ServicePort.class)
                    .decode((ObjectNode) sport, this))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new IllegalArgumentException(error);
        }
    }

    @Override
    public ServicePort servicePort(PortId portId) {
        String response = restGet(URL_SERVICE_PORTS + portId.id());
        final String error = String.format(ERR_LOG, SERVICE_PORT, response);
        try {
            JsonNode jsonTree = mapper().readTree(response).get(SERVICE_PORT);
            if (jsonTree == null) {
                throw new IllegalArgumentException(error);
            }

            log.trace(MSG_RECEIVED + SERVICE_PORT);
            log.trace(mapper().writeValueAsString(jsonTree));
            return codec(ServicePort.class).decode((ObjectNode) jsonTree, this);
        } catch (IOException e) {
            throw new IllegalArgumentException(error);
        }
    }

    private String restGet(String path) {
        WebTarget wt = client.target(endpoint + URL_BASE).path(path);
        Invocation.Builder builder = wt.request(JSON_UTF_8.toString());
        try {
            Response response = builder.get();
            if (response.getStatus() != HTTP_OK) {
                log.warn("Failed to get resource {}", endpoint + path);
                return EMPTY_JSON_STRING;
            }
        } catch (javax.ws.rs.ProcessingException e) {
            return EMPTY_JSON_STRING;
        }
        return builder.get(String.class);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof XosVtnClient) {
            XosVtnClient that = (XosVtnClient) obj;
            if (Objects.equals(endpoint, that.endpoint) &&
                    Objects.equals(user, that.user) &&
                    Objects.equals(password, that.password)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, user, password);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("endpoint", endpoint)
                .add("user", user)
                .add("password", password)
                .toString();
    }

    /**
     * Returns new XOS vtn client builder instance.
     *
     * @return xos vtn client builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of the XOS vtn client entities.
     */
    public static final class Builder {

        private String endpoint;
        private String user;
        private String password;

        private Builder() {
        }

        /**
         * Builds immutable XOS vtn client.
         *
         * @return xos client instance
         */
        public XosVtnClient build() {
            checkArgument(!Strings.isNullOrEmpty(endpoint));
            checkArgument(!Strings.isNullOrEmpty(user));
            checkArgument(!Strings.isNullOrEmpty(password));

            // TODO perform authentication when XOS provides it
            return new XosVtnClient(endpoint, user, password);
        }

        /**
         * Returns XOS vtn client builder with the supplied endpoint.
         *
         * @param endpoint endpoint url
         * @return xos vtn client builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Returns XOS vtn client builder with the supplied user credential.
         *
         * @param user user
         * @return xos vtn client builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Returns XOS vtn client builder with the supplied password credential.
         *
         * @param password password
         * @return xos vtn client builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }
    }
}
