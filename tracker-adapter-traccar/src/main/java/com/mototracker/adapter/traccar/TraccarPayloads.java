package com.mototracker.adapter.traccar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Formato JSON que envía Traccar con {@code forward.type=json} y {@code event.forward.url}.
 * Solo se mapean los campos que usa el dominio.
 */
final class TraccarPayloads {

    private TraccarPayloads() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PositionMessage(TraccarPosition position, TraccarDevice device) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventMessage(TraccarEvent event, TraccarPosition position, TraccarDevice device) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TraccarDevice(Long id, String name, String uniqueId, String status) {
    }

    /** Traccar entrega la velocidad en nudos. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TraccarPosition(
            Long id,
            Long deviceId,
            String protocol,
            OffsetDateTime serverTime,
            OffsetDateTime deviceTime,
            OffsetDateTime fixTime,
            Boolean valid,
            Double latitude,
            Double longitude,
            Double speed,
            Double course,
            Map<String, Object> attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TraccarEvent(Long id, String type, OffsetDateTime eventTime, Long deviceId, Map<String, Object> attributes) {
    }
}
