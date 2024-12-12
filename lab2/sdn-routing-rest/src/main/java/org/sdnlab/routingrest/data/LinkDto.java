package org.sdnlab.routingrest.data;

import org.onosproject.net.Link;

public class LinkDto {
    public final String type;
    public final String state;

    public final ConnectPointDto src;
    public final ConnectPointDto dst;

    public LinkDto(Link link) {
        type = link.type().name();
        state = link.state().name();

        src = new ConnectPointDto(link.src());
        dst = new ConnectPointDto(link.dst());
    }
}
