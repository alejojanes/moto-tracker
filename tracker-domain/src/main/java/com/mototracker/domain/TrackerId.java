package com.mototracker.domain;

/**
 * Identidad del rastreador: su IMEI (15 dígitos).
 */
public record TrackerId(String imei) {

    public TrackerId {
        if (imei == null || !imei.matches("\\d{10,20}")) {
            throw new IllegalArgumentException("IMEI inválido: " + imei);
        }
    }

    @Override
    public String toString() {
        return imei;
    }
}
