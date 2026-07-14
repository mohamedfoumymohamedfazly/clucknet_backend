package com.clucknet.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryDataResponse {

    private Instant timestamp;
    private Long zoneId;
    private String deviceId;
    private Double temperature;
    private Double humidity;
    private Double nh3;
    private Double lpg;
}
