package com.mototracker.domain;

/**
 * Origen de una posición. Solo GPS es confiable para ubicar la moto;
 * las demás son aproximadas (pueden estar a cientos de metros o kilómetros).
 */
public enum PositionSource {
    /** Fijada por satélites. */
    GPS,
    /** Estimada por antenas celulares o wifi. */
    NETWORK,
    /** El equipo la marcó como inválida (sin fix). */
    INVALID
}
