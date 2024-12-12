package org.sdnlab.routingrest.data;

import org.onosproject.net.Device;

public class DeviceDto {
    public final String id;
    public final String type;

    public DeviceDto(Device device) {
        id = device.id().toString();
        type = device.type().name();
    }
}
