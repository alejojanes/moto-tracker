package com.mototracker.adapter.web;

import com.mototracker.adapter.web.ApiDtos.AlertDto;
import com.mototracker.adapter.web.ApiDtos.CreateGeofenceRequest;
import com.mototracker.adapter.web.ApiDtos.CurrentPositionDto;
import com.mototracker.adapter.web.ApiDtos.GeofenceDto;
import com.mototracker.adapter.web.ApiDtos.PositionDto;
import com.mototracker.adapter.web.ApiDtos.TrackerDto;
import com.mototracker.adapter.web.ApiDtos.UpdateGeofenceRequest;
import com.mototracker.application.port.in.GeofenceManagement;
import com.mototracker.application.port.in.TrackingQueries;
import com.mototracker.domain.GeoPoint;
import com.mototracker.domain.GeofenceId;
import com.mototracker.domain.TrackerId;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TrackingApiController {

    private final TrackingQueries queries;
    private final GeofenceManagement geofences;
    private final SseLivePositionPublisher live;

    public TrackingApiController(TrackingQueries queries, GeofenceManagement geofences, SseLivePositionPublisher live) {
        this.queries = queries;
        this.geofences = geofences;
        this.live = live;
    }

    @GetMapping("/trackers")
    public List<TrackerDto> trackers() {
        return queries.trackers().stream().map(TrackerDto::of).toList();
    }

    @GetMapping("/trackers/{imei}/position")
    public CurrentPositionDto position(@PathVariable String imei) {
        TrackerId id = new TrackerId(imei);
        return new CurrentPositionDto(
                queries.lastReliablePosition(id).map(PositionDto::of).orElse(null),
                queries.lastPosition(id).map(PositionDto::of).orElse(null));
    }

    @GetMapping("/trackers/{imei}/route")
    public List<PositionDto> route(@PathVariable String imei,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return queries.route(new TrackerId(imei), from, to).stream().map(PositionDto::of).toList();
    }

    @GetMapping("/trackers/{imei}/alerts")
    public List<AlertDto> alerts(@PathVariable String imei, @RequestParam(defaultValue = "50") int limit) {
        return queries.recentAlerts(new TrackerId(imei), limit).stream().map(AlertDto::of).toList();
    }

    @GetMapping(path = "/trackers/{imei}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable String imei) {
        return live.subscribe(new TrackerId(imei));
    }

    @GetMapping("/trackers/{imei}/geofences")
    public List<GeofenceDto> geofences(@PathVariable String imei) {
        return geofences.list(new TrackerId(imei)).stream().map(GeofenceDto::of).toList();
    }

    @PostMapping("/trackers/{imei}/geofences")
    @ResponseStatus(HttpStatus.CREATED)
    public GeofenceDto createGeofence(@PathVariable String imei, @Valid @RequestBody CreateGeofenceRequest request) {
        return GeofenceDto.of(geofences.create(new TrackerId(imei), request.name(),
                new GeoPoint(request.latitude(), request.longitude()), request.radiusMeters()));
    }

    @PatchMapping("/geofences/{id}")
    public GeofenceDto updateGeofence(@PathVariable UUID id, @Valid @RequestBody UpdateGeofenceRequest request) {
        return GeofenceDto.of(geofences.setActive(new GeofenceId(id), request.active()));
    }

    @DeleteMapping("/geofences/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGeofence(@PathVariable UUID id) {
        geofences.delete(new GeofenceId(id));
    }
}
