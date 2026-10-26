package com.mototracker.adapter.traccar;

import com.mototracker.adapter.traccar.TraccarPayloads.TraccarDevice;
import com.mototracker.adapter.traccar.TraccarPayloads.TraccarEvent;
import com.mototracker.adapter.traccar.TraccarPayloads.TraccarPosition;
import com.mototracker.application.port.in.DeviceEvent;
import com.mototracker.domain.Position;
import com.mototracker.domain.PositionSource;
import com.mototracker.domain.TrackerId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TraccarMapperTest {

    private final TraccarMapper mapper = new TraccarMapper();
    private final TrackerId id = new TrackerId("862476051738192");
    private final OffsetDateTime t = OffsetDateTime.parse("2026-10-26T20:00:00.000+00:00");

    @Test
    void mapsGpsPositionAndConvertsKnots() {
        Position p = mapper.position(id, position(true, Map.of("ignition", true, "sat", 18))).orElseThrow();

        assertThat(p.source()).isEqualTo(PositionSource.GPS);
        assertThat(p.speedKmh()).isCloseTo(18.52, within(0.01));
        assertThat(p.ignition()).isTrue();
        assertThat(p.satellites()).isEqualTo(18);
        assertThat(p.fixTime()).isEqualTo(t.toInstant());
    }

    @Test
    void invalidAndApproximatePositionsAreNotReliable() {
        assertThat(mapper.position(id, position(false, Map.of())).orElseThrow().source())
                .isEqualTo(PositionSource.INVALID);
        assertThat(mapper.position(id, position(true, Map.of("approximate", true))).orElseThrow().source())
                .isEqualTo(PositionSource.NETWORK);
    }

    @Test
    void mapsDeviceAndAlarms() {
        assertThat(mapper.tracker(new TraccarDevice(1L, "MNX13G", " 862476051738192 ", "online")).id()).isEqualTo(id);
        assertThat(mapper.eventKind(new TraccarEvent(1L, "alarm", t, 1L, Map.of("alarm", "sos"))))
                .contains(DeviceEvent.Kind.SOS);
        assertThat(mapper.eventKind(new TraccarEvent(1L, "alarm", t, 1L, Map.of("alarm", "powerCut"))))
                .contains(DeviceEvent.Kind.POWER_CUT);
        assertThat(mapper.eventKind(new TraccarEvent(1L, "deviceOffline", t, 1L, Map.of())))
                .contains(DeviceEvent.Kind.OFFLINE);
        assertThat(mapper.eventKind(new TraccarEvent(1L, "geofenceEnter", t, 1L, Map.of()))).isEmpty();
    }

    private TraccarPosition position(boolean valid, Map<String, Object> attributes) {
        return new TraccarPosition(1L, 1L, "gt06", t, t, t, valid, 10.38, -75.48, 10.0, 90.0, attributes);
    }
}
