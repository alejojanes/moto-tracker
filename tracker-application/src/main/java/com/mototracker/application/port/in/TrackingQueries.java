package com.mototracker.application.port.in;

import com.mototracker.domain.Alert;
import com.mototracker.domain.Position;
import com.mototracker.domain.Tracker;
import com.mototracker.domain.TrackerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrackingQueries {

    List<Tracker> trackers();

    /** Última posición GPS confiable: la que se debe mostrar como "la moto está aquí". */
    Optional<Position> lastReliablePosition(TrackerId trackerId);

    /** Última posición de cualquier tipo, útil para diagnosticar. */
    Optional<Position> lastPosition(TrackerId trackerId);

    /** Recorrido entre dos instantes (solo posiciones confiables), ordenado por hora. */
    List<Position> route(TrackerId trackerId, Instant from, Instant to);

    List<Alert> recentAlerts(TrackerId trackerId, int limit);
}
