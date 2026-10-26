package com.mototracker.domain;

import java.util.UUID;

public record GeofenceId(UUID value) {

    public static GeofenceId newId() {
        return new GeofenceId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
