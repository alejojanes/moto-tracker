package com.mototracker.domain;

/**
 * Punto geográfico WGS84. Inmutable y validado.
 */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    public GeoPoint {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitud fuera de rango: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + longitude);
        }
    }

    /** Distancia en metros por la fórmula de haversine. */
    public double distanceTo(GeoPoint other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Las coordenadas 0,0 casi siempre son datos basura del equipo. */
    public boolean isNullIsland() {
        return latitude == 0 && longitude == 0;
    }
}
