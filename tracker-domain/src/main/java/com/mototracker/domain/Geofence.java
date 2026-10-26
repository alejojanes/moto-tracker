package com.mototracker.domain;

import java.util.Objects;

/**
 * Zona circular (por ejemplo "Casa" o "Trabajo").
 */
public record Geofence(GeofenceId id, TrackerId trackerId, String name, GeoPoint center, double radiusMeters, boolean active) {

    public static final double MIN_RADIUS_METERS = 50;
    public static final double MAX_RADIUS_METERS = 50_000;

    public Geofence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(center, "center");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("La geocerca necesita un nombre");
        }
        if (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS) {
            throw new IllegalArgumentException(
                    "El radio debe estar entre %.0f y %.0f metros".formatted(MIN_RADIUS_METERS, MAX_RADIUS_METERS));
        }
        name = name.strip();
    }

    public boolean contains(GeoPoint point) {
        return center.distanceTo(point) <= radiusMeters;
    }

    public Geofence withActive(boolean value) {
        return new Geofence(id, trackerId, name, center, radiusMeters, value);
    }
}
