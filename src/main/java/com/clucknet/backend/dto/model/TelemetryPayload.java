package com.clucknet.backend.dto.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryPayload {

    private Long zoneId;
    private String deviceId;
    private Long timestamp;

    private Double temperature;
    private Double humidity;
    private Double nh3;
    private Double lpg;

    // This intercepts the "metrics" JSON block and flattens it into this class
    @JsonProperty("metrics")
    private void unpackNestedMetrics(Map<String, Double> metrics) {
        if (metrics != null) {
            this.temperature = metrics.get("temp");
            this.humidity = metrics.get("hum");
            this.nh3 = metrics.get("nh3");
            this.lpg = metrics.get("lpg");
        }
    }
}