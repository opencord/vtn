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
package org.opencord.cordvtn.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of REST client for XOS VTN networking service.
 */
public final class XosVtnNetworkingClient extends AbstractWebResource {

    protected final Logger log = getLogger(getClass());

    private static final String URL_BASE = "/xosapi/v1/vtn/";

    private static final String EMPTY_JSON_STRING = "{}";

    private static final String VTN_ID = "id";
    private static final String VTN_RESYNC = "resync";
    private static final String VTN_SERVICES = "vtnservices";

    private static final String ERR_LOG = "Received %s result with wrong format: %s";

    private static final int DEFAULT_TIMEOUT_MS = 2000;

    private final String endpoint;
    private final String user;
    private final String password;
    private final Client client = ClientBuilder.newClient();

    private XosVtnNetworkingClient(String endpoint, String user, String password) {
        this.endpoint = endpoint;
        this.user = user;
        this.password = password;

        client.register(HttpAuthenticationFeature.basic(user, password));
        client.property(ClientProperties.CONNECT_TIMEOUT, DEFAULT_TIMEOUT_MS);
        client.property(ClientProperties.READ_TIMEOUT, DEFAULT_TIMEOUT_MS);
        mapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    private String restGet(String path) {
        WebTarget wt = client.target("http://" + endpoint + URL_BASE).path(path);
        Invocation.Builder builder = wt.request(JSON_UTF_8.toString());
        try {
            Response response = builder.get();
            if (response.getStatus() != HTTP_OK) {
                log.warn("Failed to get resource {}", endpoint + URL_BASE + path);
                log.warn("reason {}", response.readEntity(String.class));
                return EMPTY_JSON_STRING;
            }
        } catch (javax.ws.rs.ProcessingException e) {
            return EMPTY_JSON_STRING;
        }
        return builder.get(String.class);
    }

    private String restPut(String path, JsonNode request) {
        WebTarget wt = client.target("http://" + endpoint + URL_BASE).path(path);
        Invocation.Builder builder = wt.request(MediaType.APPLICATION_JSON)
                .accept(JSON_UTF_8.toString());

        try {
            Response response = builder.put(Entity.entity(request.toString(),
                    MediaType.APPLICATION_JSON_TYPE));
            String strResponse = response.readEntity(String.class);
            if (response.getStatus() != HTTP_OK) {
                throw new IllegalArgumentException("Failed to put resource "
                        + response.getStatus() + ": " + strResponse);
            }
            return strResponse;
        } catch (javax.ws.rs.ProcessingException e) {
            return EMPTY_JSON_STRING;
        }
    }

    public void requestSync() {
        String response = restGet(VTN_SERVICES);
        final String error = String.format(ERR_LOG, VTN_SERVICES, response);
        try {
            JsonNode jsonTree = mapper().readTree(response).path("items");
            if (!jsonTree.isArray() || jsonTree.size() < 1) {
                throw new IllegalArgumentException(error);
            }
            JsonNode vtnService = jsonTree.get(0);
            String vtnServiceId = vtnService.path(VTN_ID).asText();
            if (vtnServiceId == null || vtnServiceId.equals("")) {
                throw new IllegalArgumentException(error);
            }
            log.info("Requesting sync for VTN service {}", vtnServiceId);

            ObjectNode request = mapper().createObjectNode()
                    .put(VTN_RESYNC, true);

            restPut(VTN_SERVICES + "/" + vtnServiceId, request);
        } catch (IOException e) {
            throw new IllegalArgumentException(error);
        }
    }

    /**
     * Returns endpoint url for the XOS service API access.
     *
     * @return endpoint url as a string
     */
    public String endpoint() {
        return endpoint;
    }

    /**
     * Returns user name for the XOS service API access.
     *
     * @return user name as a string
     */
    public String user() {
        return user;
    }

    /**
     * Returns password for the XOS service API access.
     *
     * @return password as a string
     */
    public String password() {
        return password;
    }

    /**
     * Returns new XOS service networking builder instance.
     *
     * @return xos service networking builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of the XOS service networking entities.
     */
    public static final class Builder {

        private String endpoint;
        private String user;
        private String password;

        private Builder() {
        }

        /**
         * Builds immutable XOS service networking instance.
         *
         * @return xos service networking instance
         */
        public XosVtnNetworkingClient build() {
            checkArgument(!Strings.isNullOrEmpty(endpoint));
            checkArgument(!Strings.isNullOrEmpty(user));
            checkArgument(!Strings.isNullOrEmpty(password));

            return new XosVtnNetworkingClient(endpoint, user, password);
        }

        /**
         * Returns XOS service networking builder with the supplied endpoint.
         *
         * @param endpoint endpoint url
         * @return xos service networking builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Returns XOS service networking builder with the supplied user
         * credential.
         *
         * @param user user
         * @return xos service networking builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Returns XOS service networking builder with the supplied password
         * credential.
         *
         * @param password password
         * @return xos service networking builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }
    }
}
