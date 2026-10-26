package com.mototracker.application.port.in;

import com.mototracker.domain.GeoPoint;
import com.mototracker.domain.Tracker;

import java.time.Instant;
import java.util.Objects;

/** Evento del equipo que no es una posición: conexión, desconexión o alarma. */
public record DeviceEvent(Tracker tracker, Kind kind, Instant time, GeoPoint point) {

    public enum Kind { ONLINE, OFFLINE, SOS, POWER_CUT }

    public DeviceEvent {
        Objects.requireNonNull(tracker, "tracker");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(time, "time");
    }
}
