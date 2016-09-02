/*
 * Copyright 2016-present Open Porting Laboratory
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
import org.opencord.cordvtn.api.CordVtnStore;
import org.opencord.cordvtn.api.PortId;
import org.opencord.cordvtn.api.ServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;

/**
 * Query and manage service ports.
 */
@Path("servicePorts")
public class ServicePortWebResource extends AbstractWebResource {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String MESSAGE = "Received service port ";
    private static final String SERVICE_PORT = "ServicePort";
    private static final String SERVICE_PORTS = "ServicePorts";

    private final CordVtnStore service = DefaultServiceDirectory.getService(CordVtnStore.class);

    @Context
    private UriInfo uriInfo;

    /**
     * Creates a service port from the JSON input stream.
     *
     * @param input service port JSON stream
     * @return 201 CREATED if the JSON is correct, 400 BAD_REQUEST if the JSON
     * is invalid or duplicated port with different properties exists
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createServicePort(InputStream input) {
        try {
            JsonNode jsonTree = mapper().enable(INDENT_OUTPUT).readTree(input);
            log.trace(MESSAGE + "CREATE " + mapper().writeValueAsString(jsonTree));

            ObjectNode portJson = (ObjectNode) jsonTree.get(SERVICE_PORT);
            if (portJson == null) {
                throw new IllegalArgumentException();
            }

            final ServicePort sport = codec(ServicePort.class).decode(portJson, this);
            service.createServicePort(sport);

            UriBuilder locationBuilder = uriInfo.getBaseUriBuilder()
                    .path(SERVICE_PORTS)
                    .path(sport.id().id());

            return created(locationBuilder.build()).build();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns all service ports.
     *
     * @return 200 OK with set of service ports
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServicePorts() {
        log.debug(MESSAGE + "GET");

        Set<ServicePort> sports = service.getServicePorts();
        return ok(encodeArray(ServicePort.class, SERVICE_PORTS, sports)).build();
    }

    /**
     * Returns the service port with the specified identifier.
     *
     * @param id port identifier
     * @return 200 OK with a service port, 404 NOT_FOUND if the requested
     * port does not exist
     */
    @GET
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServicePort(@PathParam("id") String id) {
        log.debug(MESSAGE + "GET " + id);

        ServicePort sport = service.getServicePort(PortId.of(id));
        if (sport == null) {
            return status(NOT_FOUND).build();
        }

        ObjectNode result = this.mapper().createObjectNode();
        result.set(SERVICE_PORT, codec(ServicePort.class).encode(sport, this));
        return ok(result).build();
    }

    /**
     * Removes the service port.
     *
     * @param id port identifier
     * @return 204 NO CONTENT, 400 BAD_REQUEST if the network does not exist
     */
    @DELETE
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteServicePort(@PathParam("id") String id) {
        log.debug(MESSAGE + "DELETE " + id);

        service.removeServicePort(PortId.of(id));
        return noContent().build();
    }
}
