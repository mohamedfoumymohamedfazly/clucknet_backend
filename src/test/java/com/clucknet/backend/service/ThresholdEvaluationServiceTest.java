package com.clucknet.backend.service;

import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.AlertStatus;
import com.clucknet.backend.entity.Threshold;
import com.clucknet.backend.entity.AlertType;
import com.clucknet.backend.repository.AlertRepository;
import com.clucknet.backend.repository.ThresholdRepository;
import com.clucknet.backend.service.impl.ThresholdEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ThresholdEvaluationServiceTest {

    @Mock
    private ThresholdRepository thresholdRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertService alertService;

    private ThresholdEvaluationServiceImpl evaluationService;

    private Threshold threshold;
    private final Long zoneId = 1L;
    private final String deviceId = "5E:FF:56:A2:AF:15";

    @BeforeEach
    public void setUp() {
        evaluationService = new ThresholdEvaluationServiceImpl(thresholdRepository, alertRepository, alertService);

        // Safe operational bounds: Temp 20 - 30, Hum 40 - 80, NH3 max 20
        threshold = Threshold.builder()
                .id(1L)
                .minTemperature(20.0)
                .maxTemperature(30.0)
                .minHumidity(40.0)
                .maxHumidity(80.0)
                .maxNh3(20.0)
                .maxLpg(200.0)
                .build();
    }

    @Test
    public void whenTemperatureExceedsMax_thenCreateHighTempAlert() {
        // Arrange
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));
        when(alertRepository.findByZoneIdAndTypeAndStatus(zoneId, AlertType.TEMP_HIGH, AlertStatus.ACTIVE))
                .thenReturn(List.of()); // No active alert exists yet

        // Act (Current temperature is 35.0°C, exceeding 30.0°C maximum threshold)
        evaluationService.evaluateTelemetry(zoneId, deviceId, 35.0, 50.0, 10.0, 120.0);

        // Assert
        verify(alertService, times(1)).createAlert(zoneId, deviceId, AlertType.TEMP_HIGH, 35.0, 30.0);
        verify(alertService, never()).resolveAlert(anyLong());
    }

    @Test
    public void whenTemperatureHealed_andActiveAlertExists_thenResolveAlert() {
        // Arrange
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));
        
        Alert activeAlert = Alert.builder().id(99L).type(AlertType.TEMP_HIGH).status(AlertStatus.ACTIVE).build();
        when(alertRepository.findByZoneIdAndTypeAndStatus(zoneId, AlertType.TEMP_HIGH, AlertStatus.ACTIVE))
                .thenReturn(List.of(activeAlert)); // An active alert currently exists in database

        // Act (Current temperature has dropped back to a safe 25.0°C)
        evaluationService.evaluateTelemetry(zoneId, deviceId, 25.0, 50.0, 10.0, 120.0);

        // Assert (Active temperature alert must heal and resolve automatically)
        verify(alertService, times(1)).resolveAlert(99L);
        verify(alertService, never()).createAlert(anyLong(), anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    public void whenTelemetryWithinSafeBounds_andNoActiveAlerts_thenDoNothing() {
        // Arrange
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));
        when(alertRepository.findByZoneIdAndTypeAndStatus(anyLong(), any(), any())).thenReturn(List.of());

        // Act (All values are within safe guidelines)
        evaluationService.evaluateTelemetry(zoneId, deviceId, 24.0, 60.0, 12.0, 120.0);

        // Assert
        verify(alertService, never()).createAlert(anyLong(), anyString(), any(), anyDouble(), anyDouble());
        verify(alertService, never()).resolveAlert(anyLong());
    }

    @Test
    public void whenLpgExceedsMax_thenCreateLpgDangerAlert() {
        // Arrange
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));
        lenient().when(alertRepository.findByZoneIdAndTypeAndStatus(zoneId, AlertType.LPG_DANGER, AlertStatus.ACTIVE))
                .thenReturn(List.of()); // No active alert exists yet

        // Act (Current LPG is 250.0 ppm, exceeding 200.0 ppm maximum threshold)
        evaluationService.evaluateTelemetry(zoneId, deviceId, 24.0, 50.0, 10.0, 250.0);

        // Assert
        verify(alertService, times(1)).createAlert(zoneId, deviceId, AlertType.LPG_DANGER, 250.0, 200.0);
        verify(alertService, never()).resolveAlert(anyLong());
    }

    @Test
    public void whenLpgHealed_andActiveAlertExists_thenResolveAlert() {
        // Arrange
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));
        
        Alert activeAlert = Alert.builder().id(100L).type(AlertType.LPG_DANGER).status(AlertStatus.ACTIVE).build();
        lenient().when(alertRepository.findByZoneIdAndTypeAndStatus(zoneId, AlertType.LPG_DANGER, AlertStatus.ACTIVE))
                .thenReturn(List.of(activeAlert)); // An active alert currently exists in database

        // Act (Current LPG has dropped back to a safe 120.0 ppm)
        evaluationService.evaluateTelemetry(zoneId, deviceId, 24.0, 50.0, 10.0, 120.0);

        // Assert (Active LPG alert must heal and resolve automatically)
        verify(alertService, times(1)).resolveAlert(100L);
        verify(alertService, never()).createAlert(anyLong(), anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    public void whenTemperatureHealed_andMultipleActiveAlertsExist_thenResolveAllAlerts() {
        // Arrange
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));
        
        Alert alert1 = Alert.builder().id(98L).type(AlertType.TEMP_HIGH).status(AlertStatus.ACTIVE).build();
        Alert alert2 = Alert.builder().id(99L).type(AlertType.TEMP_HIGH).status(AlertStatus.ACTIVE).build();
        when(alertRepository.findByZoneIdAndTypeAndStatus(zoneId, AlertType.TEMP_HIGH, AlertStatus.ACTIVE))
                .thenReturn(List.of(alert1, alert2));

        // Act (Current temperature has dropped back to a safe 25.0°C)
        evaluationService.evaluateTelemetry(zoneId, deviceId, 25.0, 50.0, 10.0, 120.0);

        // Assert (Both active alerts must be resolved)
        verify(alertService, times(1)).resolveAlert(98L);
        verify(alertService, times(1)).resolveAlert(99L);
        verify(alertService, never()).createAlert(anyLong(), anyString(), any(), anyDouble(), anyDouble());
    }
}
