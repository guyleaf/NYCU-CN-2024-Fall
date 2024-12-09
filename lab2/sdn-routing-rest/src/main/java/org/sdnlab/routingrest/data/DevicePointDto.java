package org.sdnlab.routingrest.data;

import org.onosproject.net.ConnectPoint;

public class DevicePointDto {
    public final String id;
    public final long port;

    public DevicePointDto(ConnectPoint point) {
        id = point.deviceId().toString();
        port = point.port().toLong();
    }
}
