package com.clucknet.backend.service.impl;

import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.AlertStatus;
import com.clucknet.backend.entity.Device;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.entity.AlertType;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.repository.AlertRepository;
import com.clucknet.backend.repository.DeviceRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.service.AlertService;
import com.clucknet.backend.service.NotificationService;
import com.clucknet.backend.repository.UserRepository;
import com.clucknet.backend.entity.User;
import com.clucknet.backend.security.role.Role;
import com.clucknet.backend.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import com.clucknet.backend.util.LatencyTracker;

import java.util.List;

@Service
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final ZoneRepository zoneRepository;
    private final DeviceRepository deviceRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // Utilized @Lazy to prevent potential circular reference warnings during cross-trigger injections
    public AlertServiceImpl(AlertRepository alertRepository,
                            ZoneRepository zoneRepository,
                            DeviceRepository deviceRepository,
                            @Lazy NotificationService notificationService,
                            UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.zoneRepository = zoneRepository;
        this.deviceRepository = deviceRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Alert createAlert(Long zoneId, String deviceId, AlertType type, Double triggeredValue, Double thresholdValue) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", deviceId));

        com.clucknet.backend.entity.Severity severity;
        switch (type) {
            case LPG_DANGER:
            case NH3_HIGH:
                severity = com.clucknet.backend.entity.Severity.CRITICAL;
                break;
            case TEMP_HIGH:
            case TEMP_LOW:
            case HUMIDITY_HIGH:
            case HUMIDITY_LOW:
                severity = com.clucknet.backend.entity.Severity.WARNING;
                break;
            default:
                severity = com.clucknet.backend.entity.Severity.INFO;
        }

        String alertMessage = buildAlertMessage(zone, type, triggeredValue, thresholdValue);

        // Create new active alert record
        Alert alert = Alert.builder()
                .type(type)
                .triggeredValue(triggeredValue)
                .thresholdValue(thresholdValue)
                .status(AlertStatus.ACTIVE)
                .severity(severity)
                .message(alertMessage)
                .zone(zone)
                .device(device)
                .build();

        Alert savedAlert = alertRepository.save(alert);

        long alertGeneratedAt = System.currentTimeMillis();
        LatencyTracker.setAlertGeneratedAt(alertGeneratedAt);
        log.info("[LATENCY_LOG] Stage 1: Alert generated. Alert ID: {}, Type: {}, Timestamp: {}", 
                savedAlert.getId(), savedAlert.getType(), alertGeneratedAt);

        // Instantly trigger notifications dispatch to all registered users
        notificationService.sendNotificationForAlert(savedAlert);

        return savedAlert;
    }

    private String buildAlertMessage(Zone zone, AlertType type, Double triggeredValue, Double thresholdValue) {
        String zoneName = zone.getName();
        switch (type) {
            case TEMP_HIGH:
                return String.format("🌡️ High temperature in %s", zoneName);
            case TEMP_LOW:
                return String.format("🌡️ Low temperature in %s", zoneName);
            case HUMIDITY_HIGH:
                return String.format("💧 High humidity in %s", zoneName);
            case HUMIDITY_LOW:
                return String.format("💧 Low humidity in %s", zoneName);
            case NH3_HIGH:
                return String.format("⚠️ High ammonia in %s", zoneName);
            case LPG_DANGER:
                return String.format("⚠️ Gas detected in %s", zoneName);
            default:
                return String.format("⚠️ Climate alert in %s", zoneName);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> getActiveAlerts() {
        return alertRepository.findAllActiveAlertsWithDetails(AlertStatus.ACTIVE);
    }

    @Override
    @Transactional
    public Alert resolveAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", alertId));

        validateAlertResolveAccess(alert);

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(java.time.LocalDateTime.now());
        return alertRepository.save(alert);
    }

    private void validateAlertResolveAccess(Alert alert) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new CustomException("Unauthenticated access.", HttpStatus.UNAUTHORIZED);
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        if (user.getRole() == Role.OWNER) {
            throw new CustomException("Owners are not permitted to change alert status.", HttpStatus.FORBIDDEN);
        }

        if (user.getRole() == Role.FARMER) {
            Zone zone = alert.getZone();
            if (zone == null) {
                throw new CustomException("Access denied. Alert zone not found.", HttpStatus.FORBIDDEN);
            }
            boolean isAssigned = user.getAssignedZones().stream()
                    .anyMatch(z -> z.getId().equals(zone.getId()));
            if (!isAssigned) {
                throw new CustomException("Access denied. You are not assigned to this zone.", HttpStatus.FORBIDDEN);
            }
            return;
        }

        throw new CustomException("Invalid role permissions.", HttpStatus.FORBIDDEN);
    }
}
