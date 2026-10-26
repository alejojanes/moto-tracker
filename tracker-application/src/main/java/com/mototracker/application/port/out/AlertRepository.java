package com.mototracker.application.port.out;

import com.mototracker.domain.Alert;
import com.mototracker.domain.AlertType;
import com.mototracker.domain.TrackerId;

import java.util.List;
import java.util.Optional;

public interface AlertRepository {

    void save(Alert alert);

    List<Alert> findRecent(TrackerId trackerId, int limit);

    Optional<Alert> findLatest(TrackerId trackerId, AlertType type);
}
