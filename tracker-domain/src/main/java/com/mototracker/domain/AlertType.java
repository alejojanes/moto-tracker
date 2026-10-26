package com.mototracker.domain;

public enum AlertType {
    GEOFENCE_ENTER,
    GEOFENCE_EXIT,
    /** La moto se mueve con el encendido apagado: posible robo o grúa. */
    MOVED_WHILE_PARKED,
    SOS,
    /** Le cortaron la alimentación al rastreador. */
    POWER_CUT,
    DEVICE_OFFLINE,
    DEVICE_ONLINE
}
