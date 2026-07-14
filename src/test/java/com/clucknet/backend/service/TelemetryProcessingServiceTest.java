package com.clucknet.backend.service;

import com.clucknet.backend.dto.model.TelemetryPayload;
import com.clucknet.backend.entity.Device;
import com.clucknet.backend.entity.DeviceStatus;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.entity.AlertType;
import com.clucknet.backend.repository.DeviceRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.service.impl.TelemetryProcessingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryProcessingServiceTest {

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private ThresholdEvaluationService thresholdEvaluationService;

    @Mock
    private AlertService alertService;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ZoneRepository zoneRepository;

    private TelemetryProcessingServiceImpl telemetryProcessingService;

    private Zone zone;
    private Device device;

    @BeforeEach
    public void setUp() {
        telemetryProcessingService = new TelemetryProcessingServiceImpl(
                telemetryService,
                thresholdEvaluationService,
                alertService,
                deviceRepository,
                zoneRepository
        );

        zone = Zone.builder().id(1L).name("Zone 1").build();
        device = Device.builder()
                .id("AA:BB:CC:DD:EE:FF")
                .name("Test Device")
                .status(DeviceStatus.ONLINE)
                .zone(zone)
                .build();
    }

    @Test
    public void whenProcessEdgeLpgAlertWithDeviceId_thenCreateAlertWithDeviceId() {
        // Arrange
        when(deviceRepository.findById("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(device));

        // Act
        telemetryProcessingService.processEdgeLpgAlert("AA:BB:CC:DD:EE:FF", 350.0, 200.0);

        // Assert
        verify(alertService, times(1)).createAlert(1L, "AA:BB:CC:DD:EE:FF", AlertType.LPG_DANGER, 350.0, 200.0);
    }

    @Test
    public void whenProcessEdgeLpgAlertWithZoneId_thenResolveDeviceAndCreateAlert() {
        // Arrange
        // "1" will parse as zone ID 1L
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.of(device));

        // Act
        telemetryProcessingService.processEdgeLpgAlert("1", 350.0, 200.0);

        // Assert
        verify(alertService, times(1)).createAlert(1L, "AA:BB:CC:DD:EE:FF", AlertType.LPG_DANGER, 350.0, 200.0);
    }

    @Test
    public void whenProcessEdgeLpgAlertWithUnknownId_thenErrorAndNoAlert() {
        // Arrange
        when(deviceRepository.findById("UNKNOWN_DEVICE")).thenReturn(Optional.empty());

        // Act
        telemetryProcessingService.processEdgeLpgAlert("UNKNOWN_DEVICE", 350.0, 200.0);

        // Assert
        verify(alertService, never()).createAlert(anyLong(), anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    public void whenProcessTelemetryWithValidAssignedDevice_thenProceedsSuccessfully() {
        // Arrange
        TelemetryPayload payload = TelemetryPayload.builder()
                .deviceId("AA:BB:CC:DD:EE:FF")
                .zoneId(1L)
                .temperature(25.0)
                .humidity(60.0)
                .nh3(12.0)
                .lpg(0.5)
                .build();

        when(deviceRepository.findById("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(device));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone));
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.of(device));

        // Act
        telemetryProcessingService.processTelemetry(payload);

        // Assert
        verify(telemetryService, times(1)).saveTelemetry(1L, "AA:BB:CC:DD:EE:FF", 25.0, 60.0, 12.0, 0.5);
        verify(thresholdEvaluationService, times(1)).evaluateTelemetry(1L, "AA:BB:CC:DD:EE:FF", 25.0, 60.0, 12.0, 0.5);
    }

    @Test
    public void whenProcessTelemetryWithConflictingDevice_thenAbortsAndLogsWarning() {
        // Arrange
        TelemetryPayload payload = TelemetryPayload.builder()
                .deviceId("CONFLICTING_DEVICE")
                .zoneId(1L)
                .temperature(25.0)
                .humidity(60.0)
                .nh3(12.0)
                .lpg(0.5)
                .build();

        Device conflictingDevice = Device.builder()
                .id("CONFLICTING_DEVICE")
                .name("Conflict Node")
                .status(DeviceStatus.ONLINE)
                .zone(Zone.builder().id(2L).name("Zone 2").build())
                .build();

        when(deviceRepository.findById("CONFLICTING_DEVICE")).thenReturn(Optional.of(conflictingDevice));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone));

        // Act
        telemetryProcessingService.processTelemetry(payload);

        // Assert
        verify(telemetryService, never()).saveTelemetry(anyLong(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(thresholdEvaluationService, never()).evaluateTelemetry(anyLong(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    public void whenProcessTelemetryWithUnregisteredDeviceAndEmptyZone_thenAutoRegistersAndLinks() {
        // Arrange
        TelemetryPayload payload = TelemetryPayload.builder()
                .deviceId("NEW_DEVICE")
                .zoneId(1L)
                .temperature(25.0)
                .humidity(60.0)
                .nh3(12.0)
                .lpg(0.5)
                .build();

        when(deviceRepository.findById("NEW_DEVICE")).thenReturn(Optional.empty());
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone));
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.empty());

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);

        // Act
        telemetryProcessingService.processTelemetry(payload);

        // Assert
        verify(deviceRepository, times(1)).save(deviceCaptor.capture());
        Device savedDevice = deviceCaptor.getValue();
        assertEquals("NEW_DEVICE", savedDevice.getId());
        assertEquals(zone, savedDevice.getZone());
        assertEquals(DeviceStatus.ONLINE, savedDevice.getStatus());

        verify(telemetryService, times(1)).saveTelemetry(1L, "NEW_DEVICE", 25.0, 60.0, 12.0, 0.5);
    }

    @Test
    public void whenProcessTelemetryWithUnregisteredDeviceAndOccupiedZone_thenAutoRegistersUnassignedAndRejects() {
        // Arrange
        TelemetryPayload payload = TelemetryPayload.builder()
                .deviceId("NEW_DEVICE")
                .zoneId(1L)
                .temperature(25.0)
                .humidity(60.0)
                .nh3(12.0)
                .lpg(0.5)
                .build();

        when(deviceRepository.findById("NEW_DEVICE")).thenReturn(Optional.empty());
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone));
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.of(device));

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);

        // Act
        telemetryProcessingService.processTelemetry(payload);

        // Assert
        verify(deviceRepository, times(1)).save(deviceCaptor.capture());
        Device savedDevice = deviceCaptor.getValue();
        assertEquals("NEW_DEVICE", savedDevice.getId());
        assertNull(savedDevice.getZone());
        assertEquals(DeviceStatus.ONLINE, savedDevice.getStatus());

        verify(telemetryService, never()).saveTelemetry(anyLong(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
}
