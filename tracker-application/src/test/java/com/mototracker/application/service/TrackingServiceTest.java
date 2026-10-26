package com.mototracker.application.service;

import com.mototracker.application.port.in.DeviceEvent;
import com.mototracker.application.port.in.IngestPositionUseCase.IngestResult;
import com.mototracker.application.port.out.AlertRepository;
import com.mototracker.application.port.out.GeofenceRepository;
import com.mototracker.application.port.out.PositionRepository;
import com.mototracker.application.port.out.TrackerRepository;
import com.mototracker.domain.Alert;
import com.mototracker.domain.AlertRules;
import com.mototracker.domain.AlertType;
import com.mototracker.domain.GeoPoint;
import com.mototracker.domain.Geofence;
import com.mototracker.domain.GeofenceId;
import com.mototracker.domain.Position;
import com.mototracker.domain.PositionSource;
import com.mototracker.domain.Tracker;
import com.mototracker.domain.TrackerId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingServiceTest {

    private static final Tracker MOTO = new Tracker(new TrackerId("862476051738192"), "MNX13G");
    private static final GeoPoint HOME = new GeoPoint(10.40, -75.50);

    private final InMemory store = new InMemory();
    private final List<Alert> notified = new ArrayList<>();
    private final List<Position> published = new ArrayList<>();
    private final TrackingService service = new TrackingService(store, store, store, store,
            notified::add, published::add, new AlertRules(),
            Clock.fixed(Instant.parse("2026-10-26T20:00:00Z"), ZoneOffset.UTC));

    @Test
    void storesPublishesAndIgnoresDuplicates() {
        Position p = gps(HOME, true, 0);

        assertThat(service.ingest(MOTO, p)).isEqualTo(IngestResult.STORED);
        assertThat(service.ingest(MOTO, p)).isEqualTo(IngestResult.DUPLICATE);
        assertThat(published).containsExactly(p);
        assertThat(service.trackers()).containsExactly(MOTO);
    }

    @Test
    void lastReliableSkipsApproximatePositions() {
        Position gps = gps(HOME, true, 0);
        Position network = new Position(MOTO.id(), gps.fixTime().plusSeconds(30), north(HOME, 2000), 0, 0,
                PositionSource.NETWORK, true, null);
        service.ingest(MOTO, gps);
        service.ingest(MOTO, network);

        assertThat(service.lastReliablePosition(MOTO.id())).contains(gps);
        assertThat(service.lastPosition(MOTO.id())).contains(network);
    }

    @Test
    void geofenceExitIsSavedAndNotified() {
        service.ingest(MOTO, gps(HOME, true, 0));
        service.create(MOTO.id(), "Casa", HOME, 100);
        service.ingest(MOTO, gps(north(HOME, 500), true, 60));

        assertThat(notified).extracting(Alert::type).containsExactly(AlertType.GEOFENCE_EXIT);
        assertThat(service.recentAlerts(MOTO.id(), 10)).hasSize(1);
    }

    @Test
    void movedWhileParkedRespectsCooldown() {
        service.ingest(MOTO, gps(HOME, false, 0));
        service.ingest(MOTO, gps(north(HOME, 300), false, 60));
        service.ingest(MOTO, gps(north(HOME, 600), false, 120));

        assertThat(notified).extracting(Alert::type).containsExactly(AlertType.MOVED_WHILE_PARKED);
    }

    @Test
    void notifierFailureDoesNotLoseThePosition() {
        TrackingService failing = new TrackingService(store, store, store, store,
                a -> { throw new IllegalStateException("Telegram caído"); }, p -> { }, new AlertRules(),
                Clock.systemUTC());
        failing.ingest(new DeviceEvent(MOTO, DeviceEvent.Kind.SOS, Instant.EPOCH, null));

        assertThat(store.alerts).extracting(Alert::type).containsExactly(AlertType.SOS);
    }

    @Test
    void routeRangeIsLimited() {
        assertThatThrownBy(() -> service.route(MOTO.id(), Instant.EPOCH, Instant.EPOCH.plusSeconds(8 * 86400)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Position gps(GeoPoint point, boolean ignition, long second) {
        return new Position(MOTO.id(), Instant.parse("2026-10-26T19:00:00Z").plusSeconds(second), point, 0, 0,
                PositionSource.GPS, ignition, 9);
    }

    private static GeoPoint north(GeoPoint p, double meters) {
        return new GeoPoint(p.latitude() + meters / 111_195.0, p.longitude());
    }

    /** Implementación en memoria de los puertos de salida, solo para pruebas. */
    static class InMemory implements TrackerRepository, PositionRepository, GeofenceRepository, AlertRepository {
        final Map<TrackerId, Tracker> trackers = new LinkedHashMap<>();
        final List<Position> positions = new ArrayList<>();
        final Map<GeofenceId, Geofence> geofences = new LinkedHashMap<>();
        final List<Alert> alerts = new ArrayList<>();

        @Override public void upsert(Tracker t) { trackers.put(t.id(), t); }
        @Override public List<Tracker> findAll() { return List.copyOf(trackers.values()); }
        @Override public Optional<Tracker> findById(TrackerId id) { return Optional.ofNullable(trackers.get(id)); }

        @Override public boolean save(Position p) {
            boolean dup = positions.stream().anyMatch(x -> x.trackerId().equals(p.trackerId()) && x.fixTime().equals(p.fixTime()));
            if (!dup) positions.add(p);
            return !dup;
        }
        @Override public Optional<Position> findLatest(TrackerId id) {
            return positions.stream().filter(p -> p.trackerId().equals(id)).max(Comparator.comparing(Position::fixTime));
        }
        @Override public Optional<Position> findLatestReliable(TrackerId id) {
            return positions.stream().filter(p -> p.trackerId().equals(id) && p.isReliable()).max(Comparator.comparing(Position::fixTime));
        }
        @Override public List<Position> findReliableBetween(TrackerId id, Instant from, Instant to) {
            return positions.stream().filter(p -> p.trackerId().equals(id) && p.isReliable()
                    && !p.fixTime().isBefore(from) && p.fixTime().isBefore(to)).toList();
        }

        @Override public void save(Geofence g) { geofences.put(g.id(), g); }
        @Override public Optional<Geofence> findById(GeofenceId id) { return Optional.ofNullable(geofences.get(id)); }
        @Override public List<Geofence> findByTracker(TrackerId id) {
            return geofences.values().stream().filter(g -> g.trackerId().equals(id)).toList();
        }
        @Override public void delete(GeofenceId id) { geofences.remove(id); }

        @Override public void save(Alert a) { alerts.add(a); }
        @Override public List<Alert> findRecent(TrackerId id, int limit) {
            return alerts.stream().filter(a -> a.trackerId().equals(id)).sorted(Comparator.comparing(Alert::time).reversed()).limit(limit).toList();
        }
        @Override public Optional<Alert> findLatest(TrackerId id, AlertType type) {
            return alerts.stream().filter(a -> a.trackerId().equals(id) && a.type() == type).max(Comparator.comparing(Alert::time));
        }
    }
}
