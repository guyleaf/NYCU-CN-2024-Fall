package org.sdnlab.routingrest.data;

import org.onosproject.net.device.DeviceEvent;

public class DeviceEventDto {
    public final String type;
    public final long time;

    public final boolean availability;
    // public final long port;
    public final DeviceDto subject;

    public DeviceEventDto(DeviceEvent event, boolean availability) {
        type = event.type().name();
        time = event.time();

        this.availability = availability;
        // port = event.port().number().toLong();
        subject = new DeviceDto(event.subject());
    }
}
