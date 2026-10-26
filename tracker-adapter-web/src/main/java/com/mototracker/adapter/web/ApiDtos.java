package com.mototracker.adapter.web;

import com.mototracker.domain.Alert;
import com.mototracker.domain.Geofence;
import com.mototracker.domain.Position;
import com.mototracker.domain.Tracker;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Contratos JSON de la API pública. Se mantienen separados del dominio a propósito. */
final class ApiDtos {

    private ApiDtos() {
    }

    record TrackerDto(String imei, String name) {
        static TrackerDto of(Tracker t) {
            return new TrackerDto(t.id().imei(), t.name());
        }
    }

    record PositionDto(Instant fixTime, double latitude, double longitude, double speedKmh, double course,
                       String source, Boolean ignition, Integer satellites) {
        static PositionDto of(Position p) {
            return p == null ? null : new PositionDto(p.fixTime(), p.point().latitude(), p.point().longitude(),
                    Math.round(p.speedKmh() * 10) / 10.0, p.course(), p.source().name(), p.ignition(), p.satellites());
        }
    }

    /**
     * @param reliable la ubicación GPS más reciente: es la que se dibuja como "la moto"
     * @param latest   la última recibida de cualquier tipo, para saber si el equipo sigue reportando
     */
    record CurrentPositionDto(PositionDto reliable, PositionDto latest) {
    }

    record AlertDto(String type, Instant time, String message, Double latitude, Double longitude) {
        static AlertDto of(Alert a) {
            return new AlertDto(a.type().name(), a.time(), a.message(),
                    a.point() == null ? null : a.point().latitude(),
                    a.point() == null ? null : a.point().longitude());
        }
    }

    record GeofenceDto(String id, String name, double latitude, double longitude, double radiusMeters, boolean active) {
        static GeofenceDto of(Geofence g) {
            return new GeofenceDto(g.id().toString(), g.name(), g.center().latitude(), g.center().longitude(),
                    g.radiusMeters(), g.active());
        }
    }

    record CreateGeofenceRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @DecimalMin("50") @DecimalMax("50000") Double radiusMeters) {
    }

    record UpdateGeofenceRequest(@NotNull Boolean active) {
    }

    record ErrorDto(String error) {
    }
}
