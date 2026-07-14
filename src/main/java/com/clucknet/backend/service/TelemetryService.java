package com.clucknet.backend.service;

import com.clucknet.backend.dto.response.TelemetryDataResponse;

import java.util.List;

public interface TelemetryService {

    // Saves fresh sensor readouts to InfluxDB asynchronously
    void saveTelemetry(Long zoneId, String deviceId, Double temperature, Double humidity, Double nh3, Double lpg);

    // Queries InfluxDB using Flux to fetch historical telemetry trends
    List<TelemetryDataResponse> getHistoricalTelemetry(Long zoneId, String range);

    // Queries InfluxDB using Flux to fetch the latest telemetry point
    TelemetryDataResponse getLatestTelemetry(Long zoneId);
}
