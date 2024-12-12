package org.sdnlab.routingrest.data;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RouteDto {
    @Nullable
    public final Long id;

    @JsonIgnore
    @Nullable
    public final PathDto path;

    @JsonProperty("points")
    public List<ConnectPointDto> points() {
        return this.path != null ? this.path.points : null;
    }

    public RouteDto(Long id) {
        this.id = id;
        this.path = null;
    }

    @JsonCreator
    public RouteDto(@JsonProperty("id") Long id, @JsonProperty("points") List<ConnectPointDto> points) {
        this.id = id;
        this.path = new PathDto(points);
    }

    public RouteDto(Long id, PathDto path) {
        this.id = id;
        this.path = path;
    }
}
