package com.mototracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * @param timezone zona horaria para los mensajes (Colombia: America/Bogota)
 * @param username usuario de la app web
 * @param password contraseña de la app web (se cifra con BCrypt al arrancar)
 * @param rememberMeKey secreto para la sesión recordada en el celular
 * @param telegram  bot de Telegram; si falta el token, las alertas solo quedan en el log
 */
@ConfigurationProperties(prefix = "tracker")
public record TrackerProperties(ZoneId timezone, String username, String password, String rememberMeKey,
                                Telegram telegram) {

    public TrackerProperties {
        timezone = timezone != null ? timezone : ZoneId.of("America/Bogota");
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Configura tracker.username (TRACKER_USERNAME)");
        }
        if (password == null || password.length() < 10) {
            throw new IllegalStateException("tracker.password (TRACKER_PASSWORD) debe tener al menos 10 caracteres");
        }
        if (rememberMeKey == null || rememberMeKey.length() < 24) {
            throw new IllegalStateException("tracker.remember-me-key debe tener al menos 24 caracteres");
        }
        telegram = telegram != null ? telegram : new Telegram(null, null);
    }

    public record Telegram(String botToken, String chatId) {
        public boolean enabled() {
            return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
        }
    }
}
