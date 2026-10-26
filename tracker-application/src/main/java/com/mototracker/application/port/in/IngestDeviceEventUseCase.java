package com.mototracker.application.port.in;

public interface IngestDeviceEventUseCase {

    void ingest(DeviceEvent event);
}
