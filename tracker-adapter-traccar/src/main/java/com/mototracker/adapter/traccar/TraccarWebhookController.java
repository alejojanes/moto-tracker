package com.mototracker.adapter.traccar;

import com.mototracker.adapter.traccar.TraccarPayloads.EventMessage;
import com.mototracker.adapter.traccar.TraccarPayloads.PositionMessage;
import com.mototracker.application.port.in.DeviceEvent;
import com.mototracker.application.port.in.IngestDeviceEventUseCase;
import com.mototracker.application.port.in.IngestPositionUseCase;
import com.mototracker.domain.Tracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Adaptador de entrada: Traccar reenvía aquí cada posición y cada evento.
 * Solo es accesible dentro de la red de Docker; además exige el token compartido.
 */
@RestController
@RequestMapping("/ingest/traccar")
public class TraccarWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(TraccarWebhookController.class);

    private final IngestPositionUseCase positions;
    private final IngestDeviceEventUseCase events;
    private final TraccarWebhookProperties properties;
    private final TraccarMapper mapper = new TraccarMapper();

    public TraccarWebhookController(IngestPositionUseCase positions, IngestDeviceEventUseCase events,
                                    TraccarWebhookProperties properties) {
        this.positions = positions;
        this.events = events;
        this.properties = properties;
    }

    @PostMapping("/positions")
    public ResponseEntity<Void> position(@RequestParam(name = "token", required = false) String token,
                                         @RequestBody PositionMessage message) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).build();
        }
        Tracker tracker = mapper.tracker(message.device());
        mapper.position(tracker.id(), message.position()).ifPresentOrElse(
                p -> LOG.debug("Posición {} de {}: {}", p.source(), tracker.id(), positions.ingest(tracker, p)),
                () -> LOG.warn("Posición sin coordenadas de {}", tracker.id()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/events")
    public ResponseEntity<Void> event(@RequestParam(name = "token", required = false) String token,
                                      @RequestBody EventMessage message) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).build();
        }
        Tracker tracker = mapper.tracker(message.device());
        LOG.debug("Evento {} de {}", message.event() == null ? null : message.event().type(), tracker.id());
        mapper.eventKind(message.event()).ifPresent(kind -> events.ingest(new DeviceEvent(
                tracker, kind, mapper.eventTime(message.event()),
                mapper.position(tracker.id(), message.position())
                        .filter(p -> p.isReliable()).map(p -> p.point()).orElse(null))));
        return ResponseEntity.ok().build();
    }

    private boolean authorized(String token) {
        return token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.webhookToken().getBytes(StandardCharsets.UTF_8));
    }
}
