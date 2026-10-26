package com.mototracker.config;

import com.mototracker.adapter.telegram.TelegramAlertNotifier;
import com.mototracker.application.port.out.AlertNotifier;
import com.mototracker.application.port.out.AlertRepository;
import com.mototracker.application.port.out.GeofenceRepository;
import com.mototracker.application.port.out.LivePositionPublisher;
import com.mototracker.application.port.out.PositionRepository;
import com.mototracker.application.port.out.TrackerRepository;
import com.mototracker.application.service.TrackingService;
import com.mototracker.domain.AlertRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Cableado de la arquitectura hexagonal: el núcleo no conoce Spring, aquí se le entregan sus adaptadores.
 */
@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TrackingService trackingService(TrackerRepository trackers, PositionRepository positions,
                                    GeofenceRepository geofences, AlertRepository alerts,
                                    AlertNotifier notifier, LivePositionPublisher live, Clock clock) {
        return new TrackingService(trackers, positions, geofences, alerts, notifier, live, new AlertRules(), clock);
    }

    @Bean
    AlertNotifier alertNotifier(TrackerProperties properties) {
        TrackerProperties.Telegram telegram = properties.telegram();
        if (telegram.enabled()) {
            LOG.info("Alertas por Telegram activadas");
            return new TelegramAlertNotifier(RestClient.builder(), telegram.botToken(), telegram.chatId(), properties.timezone());
        }
        LOG.warn("Telegram no está configurado: las alertas solo se registran en el log");
        return alert -> LOG.info("ALERTA {}: {}", alert.type(), alert.message());
    }
}
