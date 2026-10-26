package com.mototracker.adapter.traccar;

import com.mototracker.adapter.traccar.TraccarPayloads.TraccarDevice;
import com.mototracker.adapter.traccar.TraccarPayloads.TraccarEvent;
import com.mototracker.adapter.traccar.TraccarPayloads.TraccarPosition;
import com.mototracker.application.port.in.DeviceEvent;
import com.mototracker.domain.GeoPoint;
import com.mototracker.domain.Position;
import com.mototracker.domain.PositionSource;
import com.mototracker.domain.Tracker;
import com.mototracker.domain.TrackerId;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/** Traduce el modelo de Traccar al dominio. Es la única clase que conoce sus nombres de campos. */
final class TraccarMapper {

    static final double KNOTS_TO_KMH = 1.852;

    Tracker tracker(TraccarDevice device) {
        if (device == null || device.uniqueId() == null) {
            throw new IllegalArgumentException("El mensaje de Traccar no trae el dispositivo");
        }
        return new Tracker(new TrackerId(device.uniqueId().strip()), device.name());
    }

    Optional<Position> position(TrackerId trackerId, TraccarPosition p) {
        if (p == null || p.latitude() == null || p.longitude() == null) {
            return Optional.empty();
        }
        Map<String, Object> attributes = p.attributes() == null ? Map.of() : p.attributes();
        Instant fixTime = firstNonNull(p.fixTime(), p.deviceTime(), p.serverTime());
        if (fixTime == null) {
            return Optional.empty();
        }
        return Optional.of(new Position(
                trackerId,
                fixTime,
                new GeoPoint(p.latitude(), p.longitude()),
                p.speed() == null ? 0 : p.speed() * KNOTS_TO_KMH,
                p.course() == null ? 0 : p.course(),
                source(p, attributes),
                bool(attributes.get("ignition")),
                integer(attributes.get("sat"))));
    }

    Optional<DeviceEvent.Kind> eventKind(TraccarEvent event) {
        if (event == null || event.type() == null) {
            return Optional.empty();
        }
        return switch (event.type()) {
            case "deviceOnline" -> Optional.of(DeviceEvent.Kind.ONLINE);
            case "deviceOffline" -> Optional.of(DeviceEvent.Kind.OFFLINE);
            case "alarm" -> alarmKind(event.attributes());
            default -> Optional.empty();
        };
    }

    Instant eventTime(TraccarEvent event) {
        Instant time = firstNonNull(event.eventTime());
        return time != null ? time : Instant.now();
    }

    /**
     * Una posición sin fix (valid=false) o calculada por antenas (approximate=true) no es la ubicación real.
     * Mostrarla como si lo fuera es la causa típica de "la moto aparece donde no está".
     */
    private static PositionSource source(TraccarPosition p, Map<String, Object> attributes) {
        if (!Boolean.TRUE.equals(p.valid())) {
            return PositionSource.INVALID;
        }
        if (Boolean.TRUE.equals(bool(attributes.get("approximate")))) {
            return PositionSource.NETWORK;
        }
        return PositionSource.GPS;
    }

    private static Optional<DeviceEvent.Kind> alarmKind(Map<String, Object> attributes) {
        Object alarm = attributes == null ? null : attributes.get("alarm");
        if (alarm == null) {
            return Optional.empty();
        }
        return switch (alarm.toString()) {
            case "sos" -> Optional.of(DeviceEvent.Kind.SOS);
            case "powerCut", "powerOff" -> Optional.of(DeviceEvent.Kind.POWER_CUT);
            default -> Optional.empty();
        };
    }

    private static Instant firstNonNull(OffsetDateTime... times) {
        for (OffsetDateTime t : times) {
            if (t != null) {
                return t.toInstant();
            }
        }
        return null;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && s.matches("\\d+")) {
            return Integer.parseInt(s);
        }
        return null;
    }
}
