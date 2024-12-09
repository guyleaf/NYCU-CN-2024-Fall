package org.sdnlab.routingrest.data;

import org.onosproject.net.link.LinkEvent;

// TODO: Change to use serializer?
public class LinkEventDto {
    public final String type;
    public final long time;

    public final LinkDto subject;

    public LinkEventDto(LinkEvent event) {
        type = event.type().name();
        time = event.time();
        subject = new LinkDto(event.subject());
    }
}
