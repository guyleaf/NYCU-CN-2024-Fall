package org.sdnlab.routingrest.data;

import org.onosproject.net.host.HostEvent;

public class HostEventDto {
    public final String type;
    public final long time;

    public final HostDto prevSubject;
    public final HostDto subject;

    public HostEventDto(HostEvent event) {
        type = event.type().name();
        time = event.time();
        subject = new HostDto(event.subject());

        prevSubject = event.prevSubject() != null ? new HostDto(event.prevSubject()) : null;
    }
}
