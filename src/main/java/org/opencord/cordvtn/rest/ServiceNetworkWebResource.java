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
package org.opencord.cordvtn.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.rest.AbstractWebResource;
import org.opencord.cordvtn.api.core.ServiceNetworkAdminService;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.*;

/**
 * Query and manage service networks.
 */
@Path("serviceNetworks")
public class ServiceNetworkWebResource extends AbstractWebResource {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String MESSAGE = "Received service network ";
    private static final String SERVICE_NETWORK  = "ServiceNetwork";
    private static final String SERVICE_NETWORKS = "ServiceNetworks";

    private final ServiceNetworkAdminService adminService =
            DefaultServiceDirectory.getService(ServiceNetworkAdminService.class);

    @Context
    private UriInfo uriInfo;

    /**
     * Creates a service network from the JSON input stream.
     *
     * @param input service network JSON stream
     * @return 201 CREATED if the JSON is correct, 400 BAD_REQUEST if the JSON
     * is invalid or duplicated network with different properties exists
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createServiceNetwork(InputStream input) {
        try {
            JsonNode jsonTree = mapper().enable(INDENT_OUTPUT).readTree(input);
            log.trace(MESSAGE + "CREATE " + mapper().writeValueAsString(jsonTree));

            ObjectNode snetJson = (ObjectNode) jsonTree.get(SERVICE_NETWORK);
            if (snetJson == null) {
                throw new IllegalArgumentException();
            }

            final ServiceNetwork snet = codec(ServiceNetwork.class).decode(snetJson, this);
            adminService.createServiceNetwork(snet);

            UriBuilder locationBuilder = uriInfo.getBaseUriBuilder()
                    .path(SERVICE_NETWORKS)
                    .path(snet.id().id());

            return created(locationBuilder.build()).build();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Updates the service network with the specified identifier.
     *
     * @param id    network identifier
     * @param input service network JSON stream
     * @return 200 OK with a service network, 400 BAD_REQUEST if the requested
     * network does not exist
     */
    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateServiceNetwork(@PathParam("id") String id, InputStream input) {
        try {
            JsonNode jsonTree = mapper().enable(INDENT_OUTPUT).readTree(input);
            log.trace(MESSAGE + "UPDATE " + mapper().writeValueAsString(jsonTree));

            ObjectNode snetJson = (ObjectNode) jsonTree.get(SERVICE_NETWORK);
            if (snetJson == null) {
                throw new IllegalArgumentException();
            }

            final ServiceNetwork snet = codec(ServiceNetwork.class).decode(snetJson, this);
            adminService.updateServiceNetwork(snet);

            ObjectNode result = this.mapper().createObjectNode();
            result.set(SERVICE_NETWORK, codec(ServiceNetwork.class).encode(snet, this));
            return ok(result).build();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns all service networks.
     *
     * @return 200 OK with set of service networks
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServiceNetworks() {
        log.trace(MESSAGE + "GET");

        Set<ServiceNetwork> snets = adminService.serviceNetworks();
        return ok(encodeArray(ServiceNetwork.class, SERVICE_NETWORKS, snets)).build();
    }

    /**
     * Returns the service network with the specified identifier.
     *
     * @param id network identifier
     * @return 200 OK with a service network, 404 NOT_FOUND if the requested
     * network does not exist
     */
    @GET
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServiceNetwork(@PathParam("id") String id) {
        log.trace(MESSAGE + "GET " + id);

        ServiceNetwork snet = adminService.serviceNetwork(NetworkId.of(id));
        if (snet == null) {
            log.trace("Returned NOT_FOUND");
            return status(NOT_FOUND).build();
        }

        ObjectNode result = this.mapper().createObjectNode();
        result.set(SERVICE_NETWORK, codec(ServiceNetwork.class).encode(snet, this));
        log.trace("Returned OK {}", result);
        return ok(result).build();
    }

    /**
     * Removes the service network.
     *
     * @param id network identifier
     * @return 204 NO CONTENT, 400 BAD_REQUEST if the network does not exist
     */
    @DELETE
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteServiceNetwork(@PathParam("id") String id) {
        log.trace(MESSAGE + "DELETE " + id);

        adminService.removeServiceNetwork(NetworkId.of(id));
        return noContent().build();
    }
}
