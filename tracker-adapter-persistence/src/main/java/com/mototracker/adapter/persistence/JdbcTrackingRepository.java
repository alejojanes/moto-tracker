package com.mototracker.adapter.persistence;

import com.mototracker.application.port.out.AlertRepository;
import com.mototracker.application.port.out.GeofenceRepository;
import com.mototracker.application.port.out.PositionRepository;
import com.mototracker.application.port.out.TrackerRepository;
import com.mototracker.domain.Alert;
import com.mototracker.domain.AlertType;
import com.mototracker.domain.GeoPoint;
import com.mototracker.domain.Geofence;
import com.mototracker.domain.GeofenceId;
import com.mototracker.domain.Position;
import com.mototracker.domain.PositionSource;
import com.mototracker.domain.Tracker;
import com.mototracker.domain.TrackerId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida sobre PostgreSQL con SQL explícito (sin ORM): el modelo es pequeño
 * y así las consultas por tiempo y los índices quedan a la vista.
 */
@Repository
public class JdbcTrackingRepository implements TrackerRepository, PositionRepository, GeofenceRepository, AlertRepository {

    private static final String POSITION_COLUMNS =
            "imei, fix_time, latitude, longitude, speed_kmh, course, source, ignition, satellites";

    private final JdbcClient jdbc;

    public JdbcTrackingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- Rastreadores ---

    @Override
    public void upsert(Tracker tracker) {
        jdbc.sql("""
                        INSERT INTO trackers (imei, name) VALUES (:imei, :name)
                        ON CONFLICT (imei) DO UPDATE SET name = EXCLUDED.name, updated_at = now()
                        WHERE trackers.name IS DISTINCT FROM EXCLUDED.name
                        """)
                .param("imei", tracker.id().imei())
                .param("name", tracker.name())
                .update();
    }

    @Override
    public List<Tracker> findAll() {
        return jdbc.sql("SELECT imei, name FROM trackers ORDER BY name").query(TRACKER).list();
    }

    @Override
    public Optional<Tracker> findById(TrackerId id) {
        return jdbc.sql("SELECT imei, name FROM trackers WHERE imei = :imei")
                .param("imei", id.imei()).query(TRACKER).optional();
    }

    // --- Posiciones ---

    @Override
    public boolean save(Position p) {
        return jdbc.sql("INSERT INTO positions (" + POSITION_COLUMNS + ") VALUES "
                        + "(:imei, :fixTime, :lat, :lon, :speed, :course, :source, :ignition, :sat) "
                        + "ON CONFLICT (imei, fix_time) DO NOTHING")
                .param("imei", p.trackerId().imei())
                .param("fixTime", Timestamp.from(p.fixTime()))
                .param("lat", p.point().latitude())
                .param("lon", p.point().longitude())
                .param("speed", p.speedKmh())
                .param("course", p.course())
                .param("source", p.source().name())
                .param("ignition", p.ignition())
                .param("sat", p.satellites())
                .update() == 1;
    }

    @Override
    public Optional<Position> findLatest(TrackerId id) {
        return jdbc.sql("SELECT " + POSITION_COLUMNS + " FROM positions WHERE imei = :imei "
                        + "ORDER BY fix_time DESC LIMIT 1")
                .param("imei", id.imei()).query(POSITION).optional();
    }

    @Override
    public Optional<Position> findLatestReliable(TrackerId id) {
        return jdbc.sql("SELECT " + POSITION_COLUMNS + " FROM positions WHERE imei = :imei AND source = 'GPS' "
                        + "AND NOT (latitude = 0 AND longitude = 0) ORDER BY fix_time DESC LIMIT 1")
                .param("imei", id.imei()).query(POSITION).optional();
    }

    @Override
    public List<Position> findReliableBetween(TrackerId id, Instant from, Instant to) {
        return jdbc.sql("SELECT " + POSITION_COLUMNS + " FROM positions WHERE imei = :imei AND source = 'GPS' "
                        + "AND NOT (latitude = 0 AND longitude = 0) "
                        + "AND fix_time >= :from AND fix_time < :to ORDER BY fix_time")
                .param("imei", id.imei())
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(POSITION).list();
    }

    // --- Geocercas ---

    @Override
    public void save(Geofence g) {
        jdbc.sql("""
                        INSERT INTO geofences (id, imei, name, latitude, longitude, radius_meters, active)
                        VALUES (:id, :imei, :name, :lat, :lon, :radius, :active)
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, latitude = EXCLUDED.latitude,
                            longitude = EXCLUDED.longitude, radius_meters = EXCLUDED.radius_meters,
                            active = EXCLUDED.active
                        """)
                .param("id", g.id().value())
                .param("imei", g.trackerId().imei())
                .param("name", g.name())
                .param("lat", g.center().latitude())
                .param("lon", g.center().longitude())
                .param("radius", g.radiusMeters())
                .param("active", g.active())
                .update();
    }

    @Override
    public Optional<Geofence> findById(GeofenceId id) {
        return jdbc.sql("SELECT * FROM geofences WHERE id = :id").param("id", id.value()).query(GEOFENCE).optional();
    }

    @Override
    public List<Geofence> findByTracker(TrackerId id) {
        return jdbc.sql("SELECT * FROM geofences WHERE imei = :imei ORDER BY name")
                .param("imei", id.imei()).query(GEOFENCE).list();
    }

    @Override
    public void delete(GeofenceId id) {
        jdbc.sql("DELETE FROM geofences WHERE id = :id").param("id", id.value()).update();
    }

    // --- Alertas ---

    @Override
    public void save(Alert a) {
        jdbc.sql("INSERT INTO alerts (imei, type, time, message, latitude, longitude) "
                        + "VALUES (:imei, :type, :time, :message, :lat, :lon)")
                .param("imei", a.trackerId().imei())
                .param("type", a.type().name())
                .param("time", Timestamp.from(a.time()))
                .param("message", a.message())
                .param("lat", a.point() == null ? null : a.point().latitude())
                .param("lon", a.point() == null ? null : a.point().longitude())
                .update();
    }

    @Override
    public List<Alert> findRecent(TrackerId id, int limit) {
        return jdbc.sql("SELECT * FROM alerts WHERE imei = :imei ORDER BY time DESC LIMIT :limit")
                .param("imei", id.imei()).param("limit", limit).query(ALERT).list();
    }

    @Override
    public Optional<Alert> findLatest(TrackerId id, AlertType type) {
        return jdbc.sql("SELECT * FROM alerts WHERE imei = :imei AND type = :type ORDER BY time DESC LIMIT 1")
                .param("imei", id.imei()).param("type", type.name()).query(ALERT).optional();
    }

    // --- Mapeo de filas ---

    private static final RowMapper<Tracker> TRACKER =
            (rs, i) -> new Tracker(new TrackerId(rs.getString("imei")), rs.getString("name"));

    private static final RowMapper<Position> POSITION = (rs, i) -> new Position(
            new TrackerId(rs.getString("imei")),
            rs.getTimestamp("fix_time").toInstant(),
            new GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude")),
            rs.getDouble("speed_kmh"),
            rs.getDouble("course"),
            PositionSource.valueOf(rs.getString("source")),
            (Boolean) rs.getObject("ignition"),
            (Integer) rs.getObject("satellites"));

    private static final RowMapper<Geofence> GEOFENCE = (rs, i) -> new Geofence(
            new GeofenceId(rs.getObject("id", UUID.class)),
            new TrackerId(rs.getString("imei")),
            rs.getString("name"),
            new GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude")),
            rs.getDouble("radius_meters"),
            rs.getBoolean("active"));

    private static final RowMapper<Alert> ALERT = (rs, i) -> new Alert(
            new TrackerId(rs.getString("imei")),
            AlertType.valueOf(rs.getString("type")),
            rs.getTimestamp("time").toInstant(),
            rs.getString("message"),
            point(rs));

    private static GeoPoint point(ResultSet rs) throws SQLException {
        double lat = rs.getDouble("latitude");
        if (rs.wasNull()) {
            return null;
        }
        return new GeoPoint(lat, rs.getDouble("longitude"));
    }
}
