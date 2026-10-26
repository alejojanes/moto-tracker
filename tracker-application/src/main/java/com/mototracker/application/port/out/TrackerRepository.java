package com.mototracker.application.port.out;

import com.mototracker.domain.Tracker;
import com.mototracker.domain.TrackerId;

import java.util.List;
import java.util.Optional;

public interface TrackerRepository {

    void upsert(Tracker tracker);

    List<Tracker> findAll();

    Optional<Tracker> findById(TrackerId id);
}
