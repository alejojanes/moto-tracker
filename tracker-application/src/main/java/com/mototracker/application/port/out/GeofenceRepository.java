package com.mototracker.application.port.out;

import com.mototracker.domain.Geofence;
import com.mototracker.domain.GeofenceId;
import com.mototracker.domain.TrackerId;

import java.util.List;
import java.util.Optional;

public interface GeofenceRepository {

    void save(Geofence geofence);

    Optional<Geofence> findById(GeofenceId id);

    List<Geofence> findByTracker(TrackerId trackerId);

    void delete(GeofenceId id);
}
