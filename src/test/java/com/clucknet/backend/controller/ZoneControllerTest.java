package com.clucknet.backend.controller;

import com.clucknet.backend.dto.response.LiveTelemetryResponse;
import com.clucknet.backend.dto.response.TelemetryDataResponse;
import com.clucknet.backend.dto.response.ZoneResponse;
import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.AlertType;
import com.clucknet.backend.repository.AlertRepository;
import com.clucknet.backend.service.DeviceService;
import com.clucknet.backend.service.TelemetryService;
import com.clucknet.backend.service.ZoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ZoneControllerTest {

    @Mock
    private ZoneService zoneService;

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private AlertRepository alertRepository;

    private ZoneController zoneController;

    @BeforeEach
    public void setUp() {
        zoneController = new ZoneController(zoneService, telemetryService, deviceService, alertRepository);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void whenTelemetryIsNewerThanAlert_thenReturnTelemetryLpg() {
        // Arrange
        Long zoneId = 1L;
        ZoneResponse zoneResponse = ZoneResponse.builder().id(zoneId).name("Zone 1").build();
        when(zoneService.getZoneById(zoneId)).thenReturn(zoneResponse);

        Instant telemetryTime = Instant.parse("2026-06-10T10:00:00Z");
        TelemetryDataResponse telemetryResponse = TelemetryDataResponse.builder()
                .temperature(25.0)
                .humidity(60.0)
                .nh3(15.0)
                .lpg(120.0)
                .timestamp(telemetryTime)
                .build();
        when(telemetryService.getLatestTelemetry(zoneId)).thenReturn(telemetryResponse);

        // Alert is older (9:59:00 AM)
        LocalDateTime alertTime = LocalDateTime.ofInstant(Instant.parse("2026-06-10T09:59:00Z"), ZoneId.systemDefault());
        Alert olderAlert = Alert.builder()
                .id(101L)
                .type(AlertType.LPG_DANGER)
                .triggeredValue(350.0)
                .createdAt(alertTime)
                .build();
        when(alertRepository.findFirstByZoneIdAndTypeOrderByCreatedAtDesc(zoneId, AlertType.LPG_DANGER))
                .thenReturn(Optional.of(olderAlert));

        // Act
        ResponseEntity<?> result = zoneController.getLiveTelemetry(zoneId);

        // Assert
        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        com.clucknet.backend.dto.response.ApiResponse<LiveTelemetryResponse> apiResponse = 
                (com.clucknet.backend.dto.response.ApiResponse<LiveTelemetryResponse>) result.getBody();
        assertNotNull(apiResponse);
        assertEquals(120.0, apiResponse.getData().getLpg());
        assertEquals(telemetryTime, apiResponse.getData().getTimestamp());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void whenAlertIsNewerThanTelemetry_thenReturnAlertLpg() {
        // Arrange
        Long zoneId = 1L;
        ZoneResponse zoneResponse = ZoneResponse.builder().id(zoneId).name("Zone 1").build();
        when(zoneService.getZoneById(zoneId)).thenReturn(zoneResponse);

        Instant telemetryTime = Instant.parse("2026-06-10T10:00:00Z");
        TelemetryDataResponse telemetryResponse = TelemetryDataResponse.builder()
                .temperature(25.0)
                .humidity(60.0)
                .nh3(15.0)
                .lpg(120.0)
                .timestamp(telemetryTime)
                .build();
        when(telemetryService.getLatestTelemetry(zoneId)).thenReturn(telemetryResponse);

        // Alert is newer (10:00:15 AM)
        Instant alertInstant = Instant.parse("2026-06-10T10:00:15Z");
        LocalDateTime alertTime = LocalDateTime.ofInstant(alertInstant, ZoneId.systemDefault());
        Alert newerAlert = Alert.builder()
                .id(102L)
                .type(AlertType.LPG_DANGER)
                .triggeredValue(350.0)
                .createdAt(alertTime)
                .build();
        when(alertRepository.findFirstByZoneIdAndTypeOrderByCreatedAtDesc(zoneId, AlertType.LPG_DANGER))
                .thenReturn(Optional.of(newerAlert));

        // Act
        ResponseEntity<?> result = zoneController.getLiveTelemetry(zoneId);

        // Assert
        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        com.clucknet.backend.dto.response.ApiResponse<LiveTelemetryResponse> apiResponse = 
                (com.clucknet.backend.dto.response.ApiResponse<LiveTelemetryResponse>) result.getBody();
        assertNotNull(apiResponse);
        assertEquals(350.0, apiResponse.getData().getLpg());
        assertEquals(alertInstant, apiResponse.getData().getTimestamp());
    }
}
