package org.sdnlab.routingrest.data;

import org.onosproject.net.Link;

public class LinkDto {
    public final String type;
    public final String state;

    public final DevicePointDto src;
    public final DevicePointDto dst;

    public LinkDto(Link link) {
        type = link.type().name();
        state = link.state().name();

        src = new DevicePointDto(link.src());
        dst = new DevicePointDto(link.dst());
    }
}
