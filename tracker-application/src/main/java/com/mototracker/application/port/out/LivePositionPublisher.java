package com.mototracker.application.port.out;

import com.mototracker.domain.Position;

/** Empuja posiciones a los clientes conectados en vivo. */
public interface LivePositionPublisher {

    void publish(Position position);
}
