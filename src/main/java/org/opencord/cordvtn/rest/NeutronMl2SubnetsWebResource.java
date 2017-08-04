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
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.rest.AbstractWebResource;
import org.opencord.cordvtn.api.core.ServiceNetworkAdminService;
import org.opencord.cordvtn.api.net.NetworkId;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.impl.DefaultServiceNetwork;
import org.openstack4j.core.transport.ObjectMapperSingleton;
import org.openstack4j.openstack.networking.domain.NeutronSubnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;

/**
 * Neutron ML2 mechanism driver implementation for the subnet resource.
 */
@Path("subnets")
public class NeutronMl2SubnetsWebResource extends AbstractWebResource {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String MESSAGE = "Received subnets %s request";
    private static final String SUBNETS = "subnets";

    private final ServiceNetworkAdminService adminService =
            DefaultServiceDirectory.getService(ServiceNetworkAdminService.class);

    @Context
    private UriInfo uriInfo;

    /**
     * Creates a subnet from the JSON input stream.
     *
     * @param input subnet JSON input stream
     * @return 201 CREATED if the JSON is correct, 400 BAD_REQUEST if the JSON
     * is invalid or duplicated subnet already exists
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSubnet(InputStream input) {
        log.trace(String.format(MESSAGE, "CREATE"));

        // FIXME do not allow more than one subnet per network
        final ServiceNetwork snet = readSubnet(input);
        adminService.updateServiceNetwork(snet);
        UriBuilder locationBuilder = uriInfo.getBaseUriBuilder()
                .path(SUBNETS)
                .path(snet.id().id());

        // TODO fix Neutron networking-onos to send Network UPDATE when subnet created
        return created(locationBuilder.build()).build();
    }

    /**
     * Updates the subnet with the specified identifier.
     *
     * @param id    subnet identifier
     * @param input subnet JSON input stream
     * @return 200 OK with the updated subnet, 400 BAD_REQUEST if the requested
     * subnet does not exist
     */
    @PUT
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateSubnet(@PathParam("id") String id, InputStream input) {
        log.trace(String.format(MESSAGE, "UPDATE " + id));

        final ServiceNetwork snet = readSubnet(input);
        adminService.updateServiceNetwork(snet);

        return status(OK).build();
    }

    /**
     * Removes the subnet.
     *
     * @param id subnet identifier
     * @return 204 NO_CONTENT, 400 BAD_REQUEST if the subnet does not exist
     */
    @DELETE
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSubnet(@PathParam("id") String id) {
        log.trace(String.format(MESSAGE, "DELETE " + id));
        return noContent().build();
    }

    private ServiceNetwork readSubnet(InputStream input) {
        try {
            JsonNode jsonTree = mapper().enable(INDENT_OUTPUT).readTree(input);
            log.trace(mapper().writeValueAsString(jsonTree));
            NeutronSubnet osSubnet = ObjectMapperSingleton.getContext(NeutronSubnet.class)
                    .readerFor(NeutronSubnet.class)
                    .readValue(jsonTree);

            return DefaultServiceNetwork.builder()
                    .id(NetworkId.of(osSubnet.getNetworkId()))
                    .subnet(IpPrefix.valueOf(osSubnet.getCidr()))
                    .serviceIp(IpAddress.valueOf(osSubnet.getGateway()))
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
