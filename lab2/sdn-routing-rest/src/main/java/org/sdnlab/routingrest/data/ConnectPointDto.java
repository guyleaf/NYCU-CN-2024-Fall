package org.sdnlab.routingrest.data;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConnectPointDto {

    public final String id;

    @Nullable
    public final Long port;

    @JsonCreator
    public ConnectPointDto(@JsonProperty("id") String id, @JsonProperty("port") Long port) {
        this.id = id;
        this.port = port;
    }

    public ConnectPointDto(ConnectPoint point) {
        id = point.deviceId().toString();
        port = point.port().toLong();
    }

    public HostId hostId() {
        return HostId.hostId(id);
    }

    public DeviceId deviceId() {
        return DeviceId.deviceId(id);
    }

    public PortNumber portNumber() {
        checkNotNull(port);
        return PortNumber.portNumber(port);
    }
}
