package com.mototracker.adapter.telegram;

import com.mototracker.application.port.out.AlertNotifier;
import com.mototracker.domain.Alert;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Envía las alertas a un chat de Telegram con un bot propio.
 * Crear el bot: hablar con @BotFather; obtener el chat id: escribirle al bot y abrir getUpdates.
 */
public class TelegramAlertNotifier implements AlertNotifier {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.forLanguageTag("es-CO"));

    private final RestClient client;
    private final String chatId;
    private final ZoneId zone;

    public TelegramAlertNotifier(RestClient.Builder builder, String botToken, String chatId, ZoneId zone) {
        this.client = builder.baseUrl("https://api.telegram.org/bot" + botToken).build();
        this.chatId = chatId;
        this.zone = zone;
    }

    @Override
    public void notify(Alert alert) {
        client.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text(alert), "disable_web_page_preview", true))
                .retrieve()
                .toBodilessEntity();
    }

    String text(Alert alert) {
        StringBuilder sb = new StringBuilder("🏍 ").append(alert.message())
                .append("\n🕒 ").append(TIME.format(alert.time().atZone(zone)));
        if (alert.point() != null) {
            sb.append("\n📍 https://maps.google.com/?q=")
                    .append(alert.point().latitude()).append(',').append(alert.point().longitude());
        }
        return sb.toString();
    }
}
