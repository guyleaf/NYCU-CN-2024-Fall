package org.sdnlab.routingrest;

import java.util.List;

import org.sdnlab.routingrest.data.RouteDto;
import org.sdnlab.routingrest.exception.InvalidRouteException;
import org.sdnlab.routingrest.exception.NotFoundException;

public interface RoutingService {

    /**
     * Get all routes from route table.
     *
     * @return routes
     */
    public List<RouteDto> getRoutes();

    /**
     * Add a path to route table and install flow rules to devices.
     *
     * @param path a route from host to host without route id.
     * @return route with route id only
     * @throws InvalidRouteException invalid path
     */
    public RouteDto addRoute(RouteDto path) throws InvalidRouteException;

    /**
     * Add multiple routes to route table and install flow rules to devices.
     *
     * @param routes routes from host to host without route id.
     * @return routes with route id only
     * @throws InvalidRouteException invalid path
     */
    List<RouteDto> addRoutes(List<RouteDto> routes) throws InvalidRouteException;

    /**
     * Update a route by route id.
     * Remove old flow rules and install new flow rules to devices.
     *
     * @param route a route from host to host.
     * @throws InvalidRouteException invalid path
     * @throws NotFoundException     route is not found
     */
    void updateRoute(RouteDto route) throws InvalidRouteException, NotFoundException;

    /**
     * Update multiple routes by route ids.
     * Remove old flow rules and install new flow rules to devices.
     *
     * @param routes routes from host to host.
     * @throws InvalidRouteException invalid path
     * @throws NotFoundException     route is not found
     */
    void updateRoutes(List<RouteDto> routes) throws InvalidRouteException, NotFoundException;

    /**
     * Remove a route and the corresponding flow rules.
     *
     * @param route a route with route id only
     */
    void deleteRoute(RouteDto route);

    /**
     * Remove multiple routes and the corresponding flow rules.
     *
     * @param routes routes with route id only
     */
    void deleteRoutes(List<RouteDto> routes);

    /**
     * Clear all routes and flow rules.
     */
    void clear();
}
