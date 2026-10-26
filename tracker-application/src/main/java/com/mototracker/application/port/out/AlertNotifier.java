package com.mototracker.application.port.out;

import com.mototracker.domain.Alert;

/** Avisa al dueño (Telegram, push, correo...). Una falla aquí nunca debe perder la posición. */
public interface AlertNotifier {

    void notify(Alert alert);
}
