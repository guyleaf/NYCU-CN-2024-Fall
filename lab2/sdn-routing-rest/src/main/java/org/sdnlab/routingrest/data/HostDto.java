package org.sdnlab.routingrest.data;

import org.onosproject.net.Host;

public class HostDto {
    public final String id;
    public final String mac;
    public final short vlan;

    public final DevicePointDto location;

    public HostDto(Host host) {
        id = host.id().toString();
        mac = host.mac().toString();
        vlan = host.vlan().id();

        location = new DevicePointDto(host.location());
    }
}
