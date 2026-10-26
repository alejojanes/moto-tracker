package com.mototracker.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas que convierten una posición nueva en alertas.
 * Es lógica pura: recibe la posición anterior confiable y la nueva, y no guarda estado.
 */
public final class AlertRules {

    /** Margen para no disparar entradas y salidas cuando el GPS oscila en el borde. */
    static final double GEOFENCE_HYSTERESIS_METERS = 25;
    /** Distancia que debe recorrer la moto apagada para considerarlo movimiento. */
    static final double PARKED_MOVE_METERS = 150;
    /** Velocidad con encendido apagado que se considera movimiento real. */
    static final double PARKED_MOVE_SPEED_KMH = 10;

    public List<Alert> evaluate(Position previous, Position current, List<Geofence> geofences) {
        List<Alert> alerts = new ArrayList<>();
        if (previous == null || !current.isReliable() || !previous.isReliable()) {
            return alerts;
        }
        for (Geofence geofence : geofences) {
            if (!geofence.active()) {
                continue;
            }
            double before = geofence.center().distanceTo(previous.point());
            double now = geofence.center().distanceTo(current.point());
            double r = geofence.radiusMeters();
            if (before <= r && now > r + GEOFENCE_HYSTERESIS_METERS) {
                alerts.add(alert(current, AlertType.GEOFENCE_EXIT, "La moto salió de " + geofence.name()));
            } else if (before > r + GEOFENCE_HYSTERESIS_METERS && now <= r) {
                alerts.add(alert(current, AlertType.GEOFENCE_ENTER, "La moto entró a " + geofence.name()));
            }
        }
        if (current.isIgnitionOff() && previous.isIgnitionOff()) {
            double moved = previous.point().distanceTo(current.point());
            if (moved > PARKED_MOVE_METERS || current.speedKmh() > PARKED_MOVE_SPEED_KMH) {
                alerts.add(alert(current, AlertType.MOVED_WHILE_PARKED,
                        "La moto se está moviendo con el encendido apagado (%.0f m, %.0f km/h)"
                                .formatted(moved, current.speedKmh())));
            }
        }
        return alerts;
    }

    private static Alert alert(Position position, AlertType type, String message) {
        return new Alert(position.trackerId(), type, position.fixTime(), message, position.point());
    }
}
