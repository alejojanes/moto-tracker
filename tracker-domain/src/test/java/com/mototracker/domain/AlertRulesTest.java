package com.mototracker.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRulesTest {

    private static final TrackerId MOTO = new TrackerId("862476051738192");
    private static final GeoPoint HOME = new GeoPoint(10.40, -75.50);
    private final AlertRules rules = new AlertRules();
    private final Geofence home = new Geofence(GeofenceId.newId(), MOTO, "Casa", HOME, 100, true);

    @Test
    void exitAndEnterGeofence() {
        Position inside = gps(HOME, true, 0, 0);
        Position outside = gps(offsetMeters(HOME, 400), true, 30, 60);

        assertThat(rules.evaluate(inside, outside, List.of(home)))
                .extracting(Alert::type).containsExactly(AlertType.GEOFENCE_EXIT);
        assertThat(rules.evaluate(outside, inside, List.of(home)))
                .extracting(Alert::type).containsExactly(AlertType.GEOFENCE_ENTER);
    }

    @Test
    void jitterOnTheBorderDoesNotAlert() {
        Position atBorder = gps(offsetMeters(HOME, 95), true, 0, 0);
        Position slightlyOut = gps(offsetMeters(HOME, 115), true, 0, 60);

        assertThat(rules.evaluate(atBorder, slightlyOut, List.of(home))).isEmpty();
    }

    @Test
    void movingWithIgnitionOffAlerts() {
        Position parked = gps(HOME, false, 0, 0);
        Position towed = gps(offsetMeters(HOME, 300), false, 20, 60);

        assertThat(rules.evaluate(parked, towed, List.of()))
                .extracting(Alert::type).containsExactly(AlertType.MOVED_WHILE_PARKED);
    }

    @Test
    void gpsDriftWhileParkedDoesNotAlert() {
        Position parked = gps(HOME, false, 0, 0);
        Position drift = gps(offsetMeters(HOME, 40), false, 2, 60);

        assertThat(rules.evaluate(parked, drift, List.of())).isEmpty();
    }

    @Test
    void approximatePositionsNeverTriggerAlerts() {
        Position inside = gps(HOME, false, 0, 0);
        Position network = new Position(MOTO, Instant.EPOCH.plusSeconds(60), offsetMeters(HOME, 3000),
                0, 0, PositionSource.NETWORK, false, null);

        assertThat(rules.evaluate(inside, network, List.of(home))).isEmpty();
    }

    @Test
    void distanceIsHaversine() {
        assertThat(HOME.distanceTo(offsetMeters(HOME, 1000))).isBetween(999.0, 1001.0);
    }

    private static Position gps(GeoPoint point, boolean ignition, double speed, long second) {
        return new Position(MOTO, Instant.EPOCH.plusSeconds(second), point, speed, 0, PositionSource.GPS, ignition, 10);
    }

    /** Desplaza el punto hacia el norte. */
    private static GeoPoint offsetMeters(GeoPoint p, double meters) {
        return new GeoPoint(p.latitude() + meters / 111_195.0, p.longitude());
    }
}
