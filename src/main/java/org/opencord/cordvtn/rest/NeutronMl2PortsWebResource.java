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
package org.opencord.cordvtn.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.osgi.DefaultServiceDirectory;
import org.opencord.cordvtn.api.CordVtnAdminService;
import org.opencord.cordvtn.api.PortId;
import org.onosproject.rest.AbstractWebResource;
import org.openstack4j.core.transport.ObjectMapperSingleton;
import org.openstack4j.model.network.Port;
import org.openstack4j.openstack.networking.domain.NeutronPort;
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
import java.io.InputStream;
import java.util.Set;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;


/**
 * Neutron ML2 mechanism driver implementation for the port resource.
 */
@Path("ports")
public class NeutronMl2PortsWebResource extends AbstractWebResource {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String MESSAGE = "Received ports %s request";
    private static final String PORT  = "port";
    private static final String PORTS = "ports";

    private final CordVtnAdminService adminService =
            DefaultServiceDirectory.getService(CordVtnAdminService.class);

    @Context
    private UriInfo uriInfo;

    /**
     * Creates a port from the JSON input stream.
     *
     * @param input port JSON input stream
     * @return 201 CREATED if the JSON is correct, 400 BAD_REQUEST if the JSON
     * is invalid or duplicated port already exists
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPorts(InputStream input) {
        log.trace(String.format(MESSAGE, "CREATE"));

        final NeutronPort port = readPort(input);
        adminService.createPort(port);
        UriBuilder locationBuilder = uriInfo.getBaseUriBuilder()
                .path(PORTS)
                .path(port.getId());

        return created(locationBuilder.build()).build();
    }

    /**
     * Updates the port with the specified identifier.
     *
     * @param id    port identifier
     * @param input port JSON input stream
     * @return 200 OK with the updated port, 400 BAD_REQUEST if the requested
     * port does not exist
     */
    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePort(@PathParam("id") String id, InputStream input) {
        log.trace(String.format(MESSAGE, "UPDATE " + id));

        final NeutronPort port = readPort(input);
        adminService.updatePort(port);

        ObjectNode result = this.mapper().createObjectNode();
        return ok(result.set(PORT, writePort(port))).build();
    }

    /**
     * Returns all ports.
     *
     * @return 200 OK with set of ports
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPorts() {
        log.trace(String.format(MESSAGE, "GET"));

        Set<Port> ports = adminService.ports();
        ArrayNode arrayNodes = mapper().createArrayNode();
        ports.stream().forEach(port -> {
            arrayNodes.add(writePort(port));
        });

        ObjectNode result = this.mapper().createObjectNode();
        return ok(result.set(PORTS, arrayNodes)).build();
    }

    /**
     * Returns the port with the given id.
     *
     * @param id port id
     * @return 200 OK with the port, 404 NOT_FOUND if the port does not exist
     */
    @GET
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPort(@PathParam("id") String id) {
        log.trace(String.format(MESSAGE, "GET " + id));

        Port port = adminService.port(PortId.of(id));
        if (port == null) {
            return status(NOT_FOUND).build();
        }

        ObjectNode result = this.mapper().createObjectNode();
        return ok(result.set(PORT, writePort(port))).build();
    }

    /**
     * Removes the port with the given id.
     *
     * @param id port identifier
     * @return 204 NO_CONTENT, 400 BAD_REQUEST if the port does not exist
     */
    @DELETE
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deletePorts(@PathParam("id") String id) {
        log.trace(String.format(MESSAGE, "DELETE " + id));

        adminService.removePort(PortId.of(id));
        return noContent().build();
    }

    private NeutronPort readPort(InputStream input) {
        try {
            JsonNode jsonTree = mapper().enable(INDENT_OUTPUT).readTree(input);
            log.trace(mapper().writeValueAsString(jsonTree));
            return ObjectMapperSingleton.getContext(NeutronPort.class)
                    .readerFor(NeutronPort.class)
                    .readValue(jsonTree);
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    private ObjectNode writePort(Port port) {
        try {
            String strPort = ObjectMapperSingleton.getContext(NeutronPort.class)
                    .writerFor(NeutronPort.class)
                    .writeValueAsString(port);
            log.trace(strPort);
            return (ObjectNode) mapper().readTree(strPort.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException();
        }
    }
}
