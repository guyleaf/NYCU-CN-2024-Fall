package org.sdnlab.routingrest.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PathDto {

    public final List<ConnectPointDto> points;

    @JsonCreator
    public PathDto(@JsonProperty("points") List<ConnectPointDto> points) {
        this.points = points;
    }

    public ConnectPointDto src() {
        return points.get(0);
    }

    public ConnectPointDto dst() {
        return points.get(points.size() - 1);
    }
}
