package com.mototracker.application.port.in;

import com.mototracker.domain.Position;
import com.mototracker.domain.Tracker;

/** Recibe una posición nueva del rastreador (venga de Traccar o de otro decodificador). */
public interface IngestPositionUseCase {

    IngestResult ingest(Tracker tracker, Position position);

    enum IngestResult { STORED, DUPLICATE }
}
