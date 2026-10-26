package com.mototracker.application.service;

import com.mototracker.application.port.in.DeviceEvent;
import com.mototracker.application.port.in.GeofenceManagement;
import com.mototracker.application.port.in.IngestDeviceEventUseCase;
import com.mototracker.application.port.in.IngestPositionUseCase;
import com.mototracker.application.port.in.NotFoundException;
import com.mototracker.application.port.in.TrackingQueries;
import com.mototracker.application.port.out.AlertNotifier;
import com.mototracker.application.port.out.AlertRepository;
import com.mototracker.application.port.out.GeofenceRepository;
import com.mototracker.application.port.out.LivePositionPublisher;
import com.mototracker.application.port.out.PositionRepository;
import com.mototracker.application.port.out.TrackerRepository;
import com.mototracker.domain.Alert;
import com.mototracker.domain.AlertRules;
import com.mototracker.domain.AlertType;
import com.mototracker.domain.GeoPoint;
import com.mototracker.domain.Geofence;
import com.mototracker.domain.GeofenceId;
import com.mototracker.domain.Position;
import com.mototracker.domain.Tracker;
import com.mototracker.domain.TrackerId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Casos de uso del rastreo. No depende de ningún framework: el arranque lo instancia con sus puertos.
 */
public class TrackingService implements IngestPositionUseCase, IngestDeviceEventUseCase, TrackingQueries, GeofenceManagement {

    private static final Logger LOG = Logger.getLogger(TrackingService.class.getName());
    /** Evita repetir la misma alerta de movimiento cada pocos segundos. */
    static final Duration MOVED_WHILE_PARKED_COOLDOWN = Duration.ofMinutes(10);
    static final Duration MAX_ROUTE_RANGE = Duration.ofDays(7);

    private final TrackerRepository trackers;
    private final PositionRepository positions;
    private final GeofenceRepository geofences;
    private final AlertRepository alerts;
    private final AlertNotifier notifier;
    private final LivePositionPublisher live;
    private final AlertRules rules;
    private final Clock clock;

    public TrackingService(TrackerRepository trackers, PositionRepository positions, GeofenceRepository geofences,
                           AlertRepository alerts, AlertNotifier notifier, LivePositionPublisher live,
                           AlertRules rules, Clock clock) {
        this.trackers = trackers;
        this.positions = positions;
        this.geofences = geofences;
        this.alerts = alerts;
        this.notifier = notifier;
        this.live = live;
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    public IngestResult ingest(Tracker tracker, Position position) {
        trackers.upsert(tracker);
        Position previous = positions.findLatestReliable(tracker.id()).orElse(null);
        if (!positions.save(position)) {
            return IngestResult.DUPLICATE;
        }
        live.publish(position);
        if (position.isNewerThan(previous)) {
            List<Geofence> zones = geofences.findByTracker(tracker.id());
            rules.evaluate(previous, position, zones).forEach(this::raise);
        }
        return IngestResult.STORED;
    }

    @Override
    public void ingest(DeviceEvent event) {
        trackers.upsert(event.tracker());
        TrackerId id = event.tracker().id();
        GeoPoint point = event.point() != null ? event.point()
                : positions.findLatestReliable(id).map(Position::point).orElse(null);
        Alert alert = switch (event.kind()) {
            case ONLINE -> new Alert(id, AlertType.DEVICE_ONLINE, event.time(), "El rastreador se conectó", point);
            case OFFLINE -> new Alert(id, AlertType.DEVICE_OFFLINE, event.time(),
                    "El rastreador se desconectó del servidor", point);
            case SOS -> new Alert(id, AlertType.SOS, event.time(), "Alarma SOS activada", point);
            case POWER_CUT -> new Alert(id, AlertType.POWER_CUT, event.time(),
                    "Se cortó la alimentación del rastreador", point);
        };
        raise(alert);
    }

    private void raise(Alert alert) {
        if (alert.type() == AlertType.MOVED_WHILE_PARKED && inCooldown(alert)) {
            return;
        }
        alerts.save(alert);
        if (alert.type() == AlertType.DEVICE_ONLINE) {
            return;
        }
        try {
            notifier.notify(alert);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo enviar la alerta " + alert.type(), e);
        }
    }

    private boolean inCooldown(Alert alert) {
        return alerts.findLatest(alert.trackerId(), alert.type())
                .map(last -> Duration.between(last.time(), alert.time()).compareTo(MOVED_WHILE_PARKED_COOLDOWN) < 0)
                .orElse(false);
    }

    @Override
    public List<Tracker> trackers() {
        return trackers.findAll();
    }

    @Override
    public Optional<Position> lastReliablePosition(TrackerId trackerId) {
        return positions.findLatestReliable(trackerId);
    }

    @Override
    public Optional<Position> lastPosition(TrackerId trackerId) {
        return positions.findLatest(trackerId);
    }

    @Override
    public List<Position> route(TrackerId trackerId, Instant from, Instant to) {
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(Duration.ofDays(1));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("El inicio debe ser anterior al fin");
        }
        if (Duration.between(start, end).compareTo(MAX_ROUTE_RANGE) > 0) {
            throw new IllegalArgumentException("El rango máximo es de 7 días");
        }
        return positions.findReliableBetween(trackerId, start, end);
    }

    @Override
    public List<Alert> recentAlerts(TrackerId trackerId, int limit) {
        return alerts.findRecent(trackerId, Math.max(1, Math.min(limit, 200)));
    }

    @Override
    public Geofence create(TrackerId trackerId, String name, GeoPoint center, double radiusMeters) {
        trackers.findById(trackerId).orElseThrow(() -> new NotFoundException("No existe el rastreador " + trackerId));
        Geofence geofence = new Geofence(GeofenceId.newId(), trackerId, name, center, radiusMeters, true);
        geofences.save(geofence);
        return geofence;
    }

    @Override
    public List<Geofence> list(TrackerId trackerId) {
        return geofences.findByTracker(trackerId);
    }

    @Override
    public Geofence setActive(GeofenceId id, boolean active) {
        Geofence updated = geofences.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la geocerca " + id))
                .withActive(active);
        geofences.save(updated);
        return updated;
    }

    @Override
    public void delete(GeofenceId id) {
        geofences.delete(id);
    }
}
