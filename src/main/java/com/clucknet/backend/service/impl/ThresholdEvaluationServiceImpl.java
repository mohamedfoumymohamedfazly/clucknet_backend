package com.clucknet.backend.service.impl;

import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.AlertStatus;
import com.clucknet.backend.entity.Threshold;
import com.clucknet.backend.entity.AlertType;
import com.clucknet.backend.repository.AlertRepository;
import com.clucknet.backend.repository.ThresholdRepository;
import com.clucknet.backend.service.AlertService;
import com.clucknet.backend.service.ThresholdEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ThresholdEvaluationServiceImpl implements ThresholdEvaluationService {

    private final ThresholdRepository thresholdRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;

    public ThresholdEvaluationServiceImpl(ThresholdRepository thresholdRepository,
                                           AlertRepository alertRepository,
                                           AlertService alertService) {
        this.thresholdRepository = thresholdRepository;
        this.alertRepository = alertRepository;
        this.alertService = alertService;
    }

    @Override
    @Transactional
    public void evaluateTelemetry(Long zoneId, String deviceId, Double temperature, Double humidity, Double nh3, Double lpg) {
        Optional<Threshold> thresholdOpt = thresholdRepository.findByZoneId(zoneId);
        if (thresholdOpt.isEmpty()) {
            log.warn("Telemetry Evaluation: Skipping evaluation for zone ID {} as no active thresholds exist.", zoneId);
            return;
        }

        Threshold t = thresholdOpt.get();

        // 1. Evaluate Temperature
        evaluateMetric(zoneId, deviceId, AlertType.TEMP_HIGH, temperature, t.getEffectiveMaxTemperature(), temperature > t.getEffectiveMaxTemperature());
        evaluateMetric(zoneId, deviceId, AlertType.TEMP_LOW, temperature, t.getEffectiveMinTemperature(), temperature < t.getEffectiveMinTemperature());

        // 2. Evaluate Humidity
        evaluateMetric(zoneId, deviceId, AlertType.HUMIDITY_HIGH, humidity, t.getEffectiveMaxHumidity(), humidity > t.getEffectiveMaxHumidity());
        evaluateMetric(zoneId, deviceId, AlertType.HUMIDITY_LOW, humidity, t.getEffectiveMinHumidity(), humidity < t.getEffectiveMinHumidity());

        // 3. Evaluate Toxic Ammonia Gas (NH3)
        evaluateMetric(zoneId, deviceId, AlertType.NH3_HIGH, nh3, t.getMaxNh3(), nh3 > t.getMaxNh3());

        // 4. Evaluate LP Gas (LPG)
        if (lpg != null) {
            evaluateMetric(zoneId, deviceId, AlertType.LPG_DANGER, lpg, t.getMaxLpg(), lpg > t.getMaxLpg());
        }
    }

    // Coordinates metric checks, duplicate alert prevention, and self-healing alert resolution
    private void evaluateMetric(Long zoneId, String deviceId, AlertType type, Double currentValue, Double thresholdValue, boolean isBreached) {
        List<Alert> activeAlerts = alertRepository.findByZoneIdAndTypeAndStatus(zoneId, type, AlertStatus.ACTIVE);

        if (isBreached) {
            if (activeAlerts.isEmpty()) {
                // Instantly generate and trigger a new active alert
                log.warn("Climate threat detected in Zone {}! Type: {}, Value: {} (Threshold: {})", zoneId, type, currentValue, thresholdValue);
                alertService.createAlert(zoneId, deviceId, type, currentValue, thresholdValue);
            }
            // If already active, we bypass writing duplicates to avoid alert flooding
        } else {
            // Self-healing: If metric returned to normal bounds, resolve all active alerts of this type
            for (Alert activeAlert : activeAlerts) {
                log.info("Climate healed in Zone {}! Resolving active alert ID: {} ({})", zoneId, activeAlert.getId(), type);
                alertService.resolveAlert(activeAlert.getId());
            }
        }
    }
}
