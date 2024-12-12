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

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseBroadcaster;
import org.onosproject.event.Event;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.rest.AbstractWebResource;
import org.sdnlab.routingrest.data.DeviceEventDto;
import org.sdnlab.routingrest.data.HostEventDto;
import org.sdnlab.routingrest.data.LinkEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Event web resource.
 */

@Singleton
@Path("events")
public class EventWebResource extends AbstractWebResource {
    private SseBroadcaster broadcaster = new SseBroadcaster();

    private final TopologyService topologyService = getService(TopologyService.class);
    private final DeviceService deviceService = getService(DeviceService.class);
    private final HostService hostService = getService(HostService.class);

    private TopologyListener topologyListener = new InternalTopologyListener();
    private DeviceListener deviceListener = new InternalDeviceListener();
    private HostListener hostListener = new InternalHostListener();

    private final Logger log = LoggerFactory.getLogger(getClass());

    public EventWebResource() {
        topologyService.addListener(topologyListener);
        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);
        log.info("Initialized!");
    }

    // FIXME: Support destroying
    // @PreDestroy
    // public void cleanUp() {
    // topoBroadcaster.closeAll();
    // topologyService.removeListener(topologyListener);
    // hostBroadcaster.closeAll();
    // log.info("Stopped!");
    // }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public EventOutput listenEvents() {
        final EventOutput eventOutput = new EventOutput();
        broadcaster.add(eventOutput);
        return eventOutput;
    }

    private <T> void broadcast(String name, T data) {
        try {
            String json = mapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).writeValueAsString(data);
            OutboundEvent response = new OutboundEvent.Builder()
                    .name(name)
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(json)
                    .build();
            broadcaster.broadcast(response);
        } catch (JsonProcessingException e) {
            log.error("Error occurred in converting event to json string.", e);
            return;
        }
    }

    private class InternalTopologyListener implements TopologyListener {
        @Override
        public void event(TopologyEvent event) {
            List<LinkEventDto> data = new ArrayList<>();
            for (Event reason : event.reasons()) {
                if (reason instanceof LinkEvent) {
                    LinkEvent linkEvent = (LinkEvent) reason;
                    data.add(new LinkEventDto(linkEvent));
                }
            }
            if (data.size() > 0) {
                broadcast("topology", data);
            }
        }

        @Override
        public boolean isRelevant(TopologyEvent event) {
            // only listen on device changed to reduce bandwidth usage
            return event.reasons() != null;
        }
    }

    private class InternalLinkListener implements LinkListener {
        @Override
        public void event(LinkEvent event) {
            broadcast("link", new LinkEventDto(event));
        }
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceId deviceId = event.subject().id();
            broadcast("device", new DeviceEventDto(event, deviceService.isAvailable(deviceId)));
        }

        @Override
        public boolean isRelevant(DeviceEvent event) {
            // only listen on device changed to reduce bandwidth usage
            return event.type().name().startsWith("DEVICE_");
        }
    }

    private class InternalHostListener implements HostListener {
        @Override
        public void event(HostEvent event) {
            broadcast("host", new HostEventDto(event));
        }
    }
}
