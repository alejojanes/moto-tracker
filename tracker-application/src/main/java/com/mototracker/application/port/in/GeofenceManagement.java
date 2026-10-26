package com.mototracker.application.port.in;

import com.mototracker.domain.GeoPoint;
import com.mototracker.domain.Geofence;
import com.mototracker.domain.GeofenceId;
import com.mototracker.domain.TrackerId;

import java.util.List;

public interface GeofenceManagement {

    Geofence create(TrackerId trackerId, String name, GeoPoint center, double radiusMeters);

    List<Geofence> list(TrackerId trackerId);

    Geofence setActive(GeofenceId id, boolean active);

    void delete(GeofenceId id);
}
