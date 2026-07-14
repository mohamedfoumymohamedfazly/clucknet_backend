package com.clucknet.backend.service.impl;

import com.clucknet.backend.dto.model.TelemetryPayload;
import com.clucknet.backend.entity.Device;
import com.clucknet.backend.entity.DeviceStatus;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.entity.AlertType;
import com.clucknet.backend.repository.DeviceRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.service.AlertService;
import com.clucknet.backend.service.TelemetryProcessingService;
import com.clucknet.backend.service.TelemetryService;
import com.clucknet.backend.service.ThresholdEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
public class TelemetryProcessingServiceImpl implements TelemetryProcessingService {

    private final TelemetryService telemetryService;
    private final ThresholdEvaluationService thresholdEvaluationService;
    private final AlertService alertService;
    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;

    public TelemetryProcessingServiceImpl(TelemetryService telemetryService,
                                         ThresholdEvaluationService thresholdEvaluationService,
                                         AlertService alertService,
                                         DeviceRepository deviceRepository,
                                         ZoneRepository zoneRepository) {
        this.telemetryService = telemetryService;
        this.thresholdEvaluationService = thresholdEvaluationService;
        this.alertService = alertService;
        this.deviceRepository = deviceRepository;
        this.zoneRepository = zoneRepository;
    }

    @Override
    @Transactional
    public void processTelemetry(TelemetryPayload payload) {
        log.debug("Telemetry processing pipeline triggered for device: {}", payload.getDeviceId());

        // 1. Validate device-zone mapping compatibility before any action to prevent data pollution/integrity errors
        if (!validateAndSyncDeviceZoneMapping(payload.getDeviceId(), payload.getZoneId())) {
            log.warn("Telemetry rejected for device {} and zone {}: Device mapping conflict or validation failed.",
                    payload.getDeviceId(), payload.getZoneId());
            return;
        }

        // 2. Asynchronously persist metrics to InfluxDB for time-series analytics
        telemetryService.saveTelemetry(
                payload.getZoneId(),
                payload.getDeviceId(),
                payload.getTemperature(),
                payload.getHumidity(),
                payload.getNh3(),
                payload.getLpg()
        );

        // 3. Delegate climate values to the evaluation engine to check thresholds
        thresholdEvaluationService.evaluateTelemetry(
                payload.getZoneId(),
                payload.getDeviceId(),
                payload.getTemperature(),
                payload.getHumidity(),
                payload.getNh3(),
                payload.getLpg()
        );
    }

    @Override
    @Transactional
    public void processEdgeLpgAlert(String deviceId, Double lpgValue, Double thresholdValue) {
        log.warn("Emergency LPG edge threat received from device ID: {}! Level: {} ppm", deviceId, lpgValue);

        Optional<Device> deviceOpt = Optional.empty();
        try {
            Long zoneId = Long.parseLong(deviceId);
            deviceOpt = deviceRepository.findByZoneId(zoneId);
        } catch (NumberFormatException e) {
            deviceOpt = deviceRepository.findById(deviceId);
        }

        if (deviceOpt.isEmpty()) {
            log.error("Emergency Alert Error: Received threat from unregistered device ID: {}", deviceId);
            return;
        }

        Device device = deviceOpt.get();
        if (device.getZone() == null) {
            log.error("Emergency Alert Warning: Device {} reported danger but is not linked to any active climate zone.", deviceId);
            return;
        }

        // Direct bypass: Instantly log safety alert in MySQL and broadcast warning
        alertService.createAlert(
                device.getZone().getId(),
                device.getId(),
                AlertType.LPG_DANGER,
                lpgValue,
                thresholdValue
        );
    }

    // Validates device and zone mappings to satisfy OneToOne uniqueness constraints
    private boolean validateAndSyncDeviceZoneMapping(String deviceId, Long zoneId) {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        Optional<Zone> zoneOpt = zoneRepository.findById(zoneId);

        if (zoneOpt.isEmpty()) {
            log.warn("Telemetry received for non-existent zone ID: {}", zoneId);
            if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                if (device.getStatus() == DeviceStatus.OFFLINE) {
                    device.setStatus(DeviceStatus.ONLINE);
                    deviceRepository.save(device);
                }
            } else {
                Device autoRegistered = Device.builder()
                        .id(deviceId)
                        .name("Edge Node")
                        .status(DeviceStatus.ONLINE)
                        .zone(null)
                        .build();
                deviceRepository.save(autoRegistered);
            }
            return false;
        }

        Zone zone = zoneOpt.get();
        Optional<Device> assignedDeviceOpt = deviceRepository.findByZoneId(zoneId);

        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();

            // Case 1: Device is assigned to this zone (valid mapping)
            if (device.getZone() != null && device.getZone().getId().equals(zoneId)) {
                if (device.getStatus() == DeviceStatus.OFFLINE) {
                    device.setStatus(DeviceStatus.ONLINE);
                    deviceRepository.save(device);
                }
                return true;
            }

            // Case 2: Device is assigned to a DIFFERENT zone (conflict)
            if (device.getZone() != null) {
                log.warn("Conflict: Device {} is already assigned to Zone {} ('{}'), but telemetry reports Zone {}.",
                        deviceId, device.getZone().getId(), device.getZone().getName(), zoneId);
                return false;
            }

            // Case 3: Device is currently unassigned (device.getZone() == null)
            if (assignedDeviceOpt.isPresent()) {
                // Target zone already has another device assigned (conflict)
                log.warn("Conflict: Zone {} is already associated with device {}, but unassigned device {} reported telemetry.",
                        zoneId, assignedDeviceOpt.get().getId(), deviceId);
                if (device.getStatus() == DeviceStatus.OFFLINE) {
                    device.setStatus(DeviceStatus.ONLINE);
                    deviceRepository.save(device);
                }
                return false;
            } else {
                // Target zone is free, auto-link the unassigned device
                log.info("Auto-linking existing unassigned device {} to zone {}", deviceId, zoneId);
                device.setZone(zone);
                device.setStatus(DeviceStatus.ONLINE);
                deviceRepository.save(device);
                return true;
            }
        } else {
            // Case 4: Device is unregistered
            if (assignedDeviceOpt.isPresent()) {
                // Target zone is already occupied (conflict)
                log.warn("Conflict: Zone {} is already associated with device {}, but unregistered device {} reported telemetry.",
                        zoneId, assignedDeviceOpt.get().getId(), deviceId);

                // Auto-register the device as unassigned to satisfy unique constraints
                Device autoRegistered = Device.builder()
                        .id(deviceId)
                        .name("Edge Node")
                        .status(DeviceStatus.ONLINE)
                        .zone(null)
                        .build();
                deviceRepository.save(autoRegistered);
                return false;
            } else {
                // Target zone is free, auto-register and link
                log.info("Auto-registering and linking new device {} to zone {}", deviceId, zoneId);
                Device autoRegistered = Device.builder()
                        .id(deviceId)
                        .name("Edge Node")
                        .status(DeviceStatus.ONLINE)
                        .zone(zone)
                        .build();
                deviceRepository.save(autoRegistered);
                return true;
            }
        }
    }
}
