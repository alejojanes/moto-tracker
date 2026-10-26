package com.mototracker.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Una posición reportada por el rastreador.
 *
 * @param fixTime   hora en que el GPS tomó la posición (no la hora de llegada al servidor)
 * @param speedKmh  velocidad en km/h
 * @param course    rumbo en grados (0-360)
 * @param ignition  estado del encendido (ACC); null si el equipo no lo reporta
 * @param satellites satélites usados; null si no se conoce
 */
public record Position(
        TrackerId trackerId,
        Instant fixTime,
        GeoPoint point,
        double speedKmh,
        double course,
        PositionSource source,
        Boolean ignition,
        Integer satellites) {

    public Position {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(fixTime, "fixTime");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(source, "source");
        if (speedKmh < 0) {
            speedKmh = 0;
        }
    }

    /** Solo las posiciones GPS válidas sirven para mostrar dónde está la moto. */
    public boolean isReliable() {
        return source == PositionSource.GPS && !point.isNullIsland();
    }

    public boolean isIgnitionOff() {
        return Boolean.FALSE.equals(ignition);
    }

    public boolean isNewerThan(Position other) {
        return other == null || fixTime.isAfter(other.fixTime);
    }
}
