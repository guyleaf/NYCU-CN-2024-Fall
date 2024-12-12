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

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.onosproject.net.Device;
import org.onosproject.net.Host;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.rest.AbstractWebResource;
import org.sdnlab.routingrest.data.DeviceDto;
import org.sdnlab.routingrest.data.HostDto;
import org.sdnlab.routingrest.data.LinkDto;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Topology web resource. (include hosts)
 */
@Path("topology")
public class TopologyWebResource extends AbstractWebResource {

    private final LinkService linkService = getService(LinkService.class);
    private final DeviceService deviceService = getService(DeviceService.class);
    private final HostService hostService = getService(HostService.class);

    /**
     * Get hello world greeting.
     *
     * @return 200 OK
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTopology() {
        List<LinkDto> links = getLinkDtos();
        List<DeviceDto> devices = getDeviceDtos();
        List<HostDto> hosts = getHostDtos();

        ObjectNode node = mapper().createObjectNode();
        node.putPOJO("links", links);
        node.putPOJO("devices", devices);
        node.putPOJO("hosts", hosts);
        return ok(node).build();
    }

    @GET
    @Path("links")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLinks() {
        List<LinkDto> links = getLinkDtos();

        ObjectNode node = mapper().createObjectNode();
        node.putPOJO("links", links);
        return ok(node).build();
    }

    @GET
    @Path("devices")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDevices() {
        List<DeviceDto> devices = getDeviceDtos();

        ObjectNode node = mapper().createObjectNode();
        node.putPOJO("devices", devices);
        return ok(node).build();
    }

    @GET
    @Path("hosts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHosts() {
        List<HostDto> hosts = getHostDtos();

        ObjectNode node = mapper().createObjectNode();
        node.putPOJO("hosts", hosts);
        return ok(node).build();
    }

    private List<LinkDto> getLinkDtos() {
        List<LinkDto> links = new ArrayList<>();
        for (Link link : linkService.getLinks()) {
            links.add(new LinkDto(link));
        }
        return links;
    }

    private List<DeviceDto> getDeviceDtos() {
        List<DeviceDto> devices = new ArrayList<>();
        for (Device device : deviceService.getDevices()) {
            devices.add(new DeviceDto(device));
        }
        return devices;
    }

    private List<HostDto> getHostDtos() {
        List<HostDto> hosts = new ArrayList<>();
        for (Host host : hostService.getHosts()) {
            hosts.add(new HostDto(host));
        }
        return hosts;
    }
}
