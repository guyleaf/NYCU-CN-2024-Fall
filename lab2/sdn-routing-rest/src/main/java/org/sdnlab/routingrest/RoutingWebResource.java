/*
 * Copyright 2024-present Open Networking Foundation
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
package org.sdnlab.routingrest;

import org.onosproject.rest.AbstractWebResource;
import org.sdnlab.routingrest.data.RouteDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Routing web resource.
 */
@Path("routes")
public class RoutingWebResource extends AbstractWebResource {

    private final RoutingService routingService = getService(RoutingService.class);

    private <T> List<T> parseListOfObjectsFromStream(InputStream stream, Class<T> expected) {
        ObjectMapper map = mapper();
        CollectionType collectionType = map.getTypeFactory().constructCollectionType(List.class, expected);
        try {
            return map.readValue(stream, collectionType);
        } catch (Exception e) {
            throw new BadRequestException("Unable to parse Route request", e);
        }
    }

    /**
     * Gets all routes.
     *
     * @return 200 OK, a list of routes
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<RouteDto> getRoutes() {
        List<RouteDto> routes = routingService.getRoutes();
        return routes;
    }

    /**
     * Creates new routes.
     *
     * @param stream a list of new routes
     * @return 201 Created
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addRoutes(InputStream stream) {
        List<RouteDto> routes = parseListOfObjectsFromStream(stream, RouteDto.class);
        List<RouteDto> routeIds = routingService.addRoutes(routes);
        return Response.status(Response.Status.CREATED).entity(routeIds).build();
    }

    /**
     * Updates old routes with new routes.
     *
     * @param stream a list of new routes
     * @return 200 OK
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateRoutes(InputStream stream) {
        List<RouteDto> routes = parseListOfObjectsFromStream(stream, RouteDto.class);
        routingService.updateRoutes(routes);
        return Response.ok().build();
    }

    /**
     * Deletes routes by route id.
     *
     * @param stream a list of routes (route id only)
     * @return 200 OK
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteRoutes(InputStream stream) {
        List<RouteDto> routes = parseListOfObjectsFromStream(stream, RouteDto.class);
        routingService.deleteRoutes(routes);
        return Response.ok().build();
    }

    /**
     * Clear all routes.
     *
     * @return 200 OK
     */
    @POST
    @Path("reset")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response clear() {
        routingService.clear();
        return Response.ok().build();
    }
}
