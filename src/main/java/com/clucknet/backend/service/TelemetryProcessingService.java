package com.clucknet.backend.service;

import com.clucknet.backend.dto.model.TelemetryPayload;

public interface TelemetryProcessingService {

    // Processes standard incoming sensor telemetry (persists to InfluxDB and evaluates thresholds)
    void processTelemetry(TelemetryPayload payload);

    // Processes emergency danger alerts published directly by the edge hardware (e.g. LPG danger)
    void processEdgeLpgAlert(String deviceId, Double lpgValue, Double thresholdValue);
}
