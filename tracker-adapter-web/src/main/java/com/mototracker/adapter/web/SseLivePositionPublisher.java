package com.mototracker.adapter.web;

import com.mototracker.adapter.web.ApiDtos.PositionDto;
import com.mototracker.application.port.out.LivePositionPublisher;
import com.mototracker.domain.Position;
import com.mototracker.domain.TrackerId;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Envía cada posición nueva a los navegadores conectados (Server-Sent Events).
 * Un latido cada 25 s evita que los proxies cierren la conexión por inactividad.
 */
@Component
public class SseLivePositionPublisher implements LivePositionPublisher {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<TrackerId, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseLivePositionPublisher() {
        heartbeat.scheduleAtFixedRate(this::ping, 25, 25, TimeUnit.SECONDS);
    }

    SseEmitter subscribe(TrackerId trackerId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = subscribers.computeIfAbsent(trackerId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    @Override
    public void publish(Position position) {
        List<SseEmitter> list = subscribers.getOrDefault(position.trackerId(), List.of());
        PositionDto dto = PositionDto.of(position);
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("position").data(dto));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }
    }

    private void ping() {
        subscribers.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }));
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        subscribers.values().forEach(list -> list.forEach(SseEmitter::complete));
    }
}
