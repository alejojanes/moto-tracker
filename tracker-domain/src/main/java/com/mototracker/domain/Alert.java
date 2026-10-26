package com.mototracker.domain;

import java.time.Instant;
import java.util.Objects;

public record Alert(TrackerId trackerId, AlertType type, Instant time, String message, GeoPoint point) {

    public Alert {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(message, "message");
    }
}
