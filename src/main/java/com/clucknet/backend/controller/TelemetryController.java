package com.clucknet.backend.controller;

import com.clucknet.backend.dto.response.ApiResponse;
import com.clucknet.backend.dto.response.TelemetryDataResponse;
import com.clucknet.backend.dto.response.TelemetryResponse;
import com.clucknet.backend.security.role.AuthorityConstants;
import com.clucknet.backend.service.TelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GetMapping("/{zoneId}")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<List<TelemetryResponse>>> getTelemetryHistory(
            @PathVariable Long zoneId,
            @RequestParam(required = false, defaultValue = "24h") String range) {

        // Handle client range formatting (e.g., 24h -> -24h) to match InfluxDB requirements
        String validatedRange = range;
        if (validatedRange != null && !validatedRange.trim().isEmpty() && !validatedRange.startsWith("-")) {
            validatedRange = "-" + validatedRange.trim();
        }

        List<TelemetryDataResponse> dataList = telemetryService.getHistoricalTelemetry(zoneId, validatedRange);

        List<TelemetryResponse> responseList = dataList.stream()
                .map(data -> TelemetryResponse.builder()
                        .timestamp(data.getTimestamp())
                        .temperature(data.getTemperature())
                        .humidity(data.getHumidity())
                        .nh3(data.getNh3())
                        .lpg(data.getLpg())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Telemetry history retrieved successfully.", responseList));
    }
}
