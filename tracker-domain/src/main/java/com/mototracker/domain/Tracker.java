package com.mototracker.domain;

/**
 * Un rastreador instalado en un vehículo.
 */
public record Tracker(TrackerId id, String name) {

    public Tracker {
        if (id == null) {
            throw new IllegalArgumentException("El rastreador necesita un IMEI");
        }
        name = (name == null || name.isBlank()) ? id.imei() : name.strip();
    }
}
