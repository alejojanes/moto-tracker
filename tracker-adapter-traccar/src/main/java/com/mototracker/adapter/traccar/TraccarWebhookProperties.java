package com.mototracker.adapter.traccar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param webhookToken secreto compartido que Traccar agrega a la URL de reenvío (?token=...)
 */
@ConfigurationProperties(prefix = "tracker.traccar")
public record TraccarWebhookProperties(String webhookToken) {

    public TraccarWebhookProperties {
        if (webhookToken == null || webhookToken.length() < 24) {
            throw new IllegalStateException("tracker.traccar.webhook-token debe tener al menos 24 caracteres");
        }
    }
}
