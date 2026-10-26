package com.mototracker.application.port.out;

import com.mototracker.domain.Position;
import com.mototracker.domain.TrackerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PositionRepository {

    /** @return false si ya existía una posición del mismo equipo con la misma hora de fix. */
    boolean save(Position position);

    Optional<Position> findLatest(TrackerId trackerId);

    Optional<Position> findLatestReliable(TrackerId trackerId);

    List<Position> findReliableBetween(TrackerId trackerId, Instant from, Instant to);
}
