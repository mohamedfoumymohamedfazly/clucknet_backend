package com.clucknet.backend.service.impl;

import com.clucknet.backend.dto.request.ThresholdUpdateRequest;
import com.clucknet.backend.dto.response.ThresholdResponse;
import com.clucknet.backend.dto.response.GrowthScheduleStageResponse;
import com.clucknet.backend.dto.request.GrowthScheduleStageUpdateRequest;
import com.clucknet.backend.entity.Threshold;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.entity.GrowthScheduleStage;
import com.clucknet.backend.entity.User;
import com.clucknet.backend.security.role.Role;
import com.clucknet.backend.exception.CustomException;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.repository.ThresholdRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.repository.UserRepository;
import com.clucknet.backend.repository.GrowthScheduleStageRepository;
import com.clucknet.backend.service.MqttSyncPublisher;
import com.clucknet.backend.service.ThresholdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@Slf4j
public class ThresholdServiceImpl implements ThresholdService {

    private final ThresholdRepository thresholdRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final GrowthScheduleStageRepository growthScheduleStageRepository;
    private final MqttSyncPublisher mqttSyncPublisher;

    public ThresholdServiceImpl(ThresholdRepository thresholdRepository,
                                ZoneRepository zoneRepository,
                                UserRepository userRepository,
                                GrowthScheduleStageRepository growthScheduleStageRepository,
                                MqttSyncPublisher mqttSyncPublisher) {
        this.thresholdRepository = thresholdRepository;
        this.zoneRepository = zoneRepository;
        this.userRepository = userRepository;
        this.growthScheduleStageRepository = growthScheduleStageRepository;
        this.mqttSyncPublisher = mqttSyncPublisher;
    }

    @Override
    @Transactional
    public ThresholdResponse updateThreshold(Long zoneId, ThresholdUpdateRequest request) {
        validateAccess(zoneId, true);

        // 1. Verify that the climate Zone exists
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        // 2. Perform rigorous physical business rule validations
        validateThresholdLimits(request);

        // 3. Fetch current threshold (or default back if missing, though initialized on zone creation)
        Threshold threshold = thresholdRepository.findByZoneId(zoneId)
                .orElseGet(() -> Threshold.builder().zone(zone).build());

        // 4. Enforce LPG restriction for Farmers
        enforceFarmerLpgRestriction(threshold, request);

        // 5. Update the values
        threshold.setMinTemperature(request.getMinTemperature());
        threshold.setMaxTemperature(request.getMaxTemperature());
        threshold.setMinHumidity(request.getMinHumidity());
        threshold.setMaxHumidity(request.getMaxHumidity());
        threshold.setMaxNh3(request.getMaxNh3());
        threshold.setMaxLpg(request.getMaxLpg());
        threshold.setAutoThresholdEnabled(request.getAutoThresholdEnabled());
        threshold.setPlacementDate(request.getPlacementDate());
        threshold.setManualOverrideEnabled(request.getManualOverrideEnabled());

        Threshold savedThreshold = thresholdRepository.save(threshold);

        // 5. Resiliently propagate changes via MQTT to Edge Gateways
        try {
            mqttSyncPublisher.publishThresholdUpdate(zoneId, savedThreshold);
        } catch (Exception ex) {
            log.error("Resiliency warning: Failed to publish threshold update to MQTT for zone ID {}: {}. DB transaction remains intact.", zoneId, ex.getMessage());
        }

        return mapToThresholdResponse(savedThreshold);
    }

    @Override
    @Transactional(readOnly = true)
    public ThresholdResponse getThresholdByZoneId(Long zoneId) {
        validateAccess(zoneId, false);

        if (!zoneRepository.existsById(zoneId)) {
            throw new ResourceNotFoundException("Zone", "id", zoneId);
        }

        Threshold threshold = thresholdRepository.findByZoneId(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Threshold", "zoneId", zoneId));

        return mapToThresholdResponse(threshold);
    }

    @Override
    @Transactional
    public java.util.List<GrowthScheduleStageResponse> getScheduleStages(Long zoneId) {
        validateAccess(zoneId, false);

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        java.util.List<GrowthScheduleStage> stages = growthScheduleStageRepository.findByZoneIdOrderByStartDayAsc(zoneId);
        if (stages.isEmpty()) {
            stages = seedDefaultStages(zone);
        }

        return stages.stream()
                .map(this::mapToGrowthScheduleStageResponse)
                .toList();
    }

    @Override
    @Transactional
    public java.util.List<GrowthScheduleStageResponse> updateScheduleStages(
            Long zoneId,
            java.util.List<GrowthScheduleStageUpdateRequest> requests) {
        validateAccess(zoneId, true);

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        // 1. Delete existing stages
        growthScheduleStageRepository.deleteByZoneId(zoneId);

        // 2. Save new stages
        java.util.List<GrowthScheduleStage> newStages = requests.stream()
                .map(req -> {
                    if (req.getMinTemperature() >= req.getMaxTemperature()) {
                        throw new CustomException("Min temperature must be less than max temperature in all stages.", HttpStatus.BAD_REQUEST);
                    }
                    if (req.getMinHumidity() >= req.getMaxHumidity()) {
                        throw new CustomException("Min humidity must be less than max humidity in all stages.", HttpStatus.BAD_REQUEST);
                    }
                    return GrowthScheduleStage.builder()
                            .zone(zone)
                            .startDay(req.getStartDay())
                            .endDay(req.getEndDay())
                            .minTemperature(req.getMinTemperature())
                            .maxTemperature(req.getMaxTemperature())
                            .minHumidity(req.getMinHumidity())
                            .maxHumidity(req.getMaxHumidity())
                            .build();
                })
                .toList();

        java.util.List<GrowthScheduleStage> saved = growthScheduleStageRepository.saveAll(newStages);

        // 3. Since schedule updates affect effective thresholds, publish to MQTT
        Threshold threshold = thresholdRepository.findByZoneId(zoneId)
                .orElseGet(() -> Threshold.builder().zone(zone).build());
        try {
            mqttSyncPublisher.publishThresholdUpdate(zoneId, threshold);
        } catch (Exception ex) {
            log.error("Resiliency warning: Failed to publish threshold update to MQTT for zone ID {}: {}", zoneId, ex.getMessage());
        }

        return saved.stream()
                .map(this::mapToGrowthScheduleStageResponse)
                .toList();
    }

    private java.util.List<GrowthScheduleStage> seedDefaultStages(Zone zone) {
        java.util.List<GrowthScheduleStage> defaults = new java.util.ArrayList<>();
        defaults.add(GrowthScheduleStage.builder().zone(zone).startDay(0).endDay(1).minTemperature(32.0).maxTemperature(33.0).minHumidity(55.0).maxHumidity(65.0).build());
        defaults.add(GrowthScheduleStage.builder().zone(zone).startDay(2).endDay(3).minTemperature(31.0).maxTemperature(32.0).minHumidity(55.0).maxHumidity(65.0).build());
        defaults.add(GrowthScheduleStage.builder().zone(zone).startDay(4).endDay(5).minTemperature(30.0).maxTemperature(31.0).minHumidity(55.0).maxHumidity(65.0).build());
        defaults.add(GrowthScheduleStage.builder().zone(zone).startDay(6).endDay(7).minTemperature(29.0).maxTemperature(30.0).minHumidity(55.0).maxHumidity(65.0).build());
        defaults.add(GrowthScheduleStage.builder().zone(zone).startDay(8).endDay(14).minTemperature(27.0).maxTemperature(29.0).minHumidity(55.0).maxHumidity(65.0).build());
        defaults.add(GrowthScheduleStage.builder().zone(zone).startDay(15).endDay(21).minTemperature(26.0).maxTemperature(27.0).minHumidity(55.0).maxHumidity(65.0).build());
        defaults.add(GrowthScheduleStage.builder().zone(zone).startDay(22).endDay(28).minTemperature(24.0).maxTemperature(26.0).minHumidity(55.0).maxHumidity(65.0).build());

        return growthScheduleStageRepository.saveAll(defaults);
    }

    private void validateAccess(Long zoneId, boolean isWrite) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new CustomException("Unauthenticated access.", HttpStatus.UNAUTHORIZED);
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        if (user.getRole() == Role.OWNER) {
            if (isWrite) {
                throw new CustomException("Owners are not permitted to edit thresholds.", HttpStatus.FORBIDDEN);
            }
            return;
        }

        if (user.getRole() == Role.FARMER) {
            boolean isAssigned = user.getAssignedZones().stream()
                    .anyMatch(z -> z.getId().equals(zoneId));
            if (isWrite && !isAssigned) {
                throw new CustomException("Access denied. You are not assigned to this zone.", HttpStatus.FORBIDDEN);
            }
            return;
        }

        throw new CustomException("Invalid role permissions.", HttpStatus.FORBIDDEN);
    }

    private void enforceFarmerLpgRestriction(Threshold threshold, ThresholdUpdateRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        if (user.getRole() == Role.FARMER) {
            if (threshold.getMaxLpg() != null && !threshold.getMaxLpg().equals(request.getMaxLpg())) {
                throw new CustomException("LPG threshold cannot be modified by farmers.", HttpStatus.FORBIDDEN);
            }
        }
    }

    // Business physical bounds validations
    private void validateThresholdLimits(ThresholdUpdateRequest request) {
        if (request.getMinTemperature() >= request.getMaxTemperature()) {
            throw new CustomException("Minimum temperature must be strictly less than maximum temperature.", HttpStatus.BAD_REQUEST);
        }

        if (request.getMinHumidity() >= request.getMaxHumidity()) {
            throw new CustomException("Minimum humidity must be strictly less than maximum humidity.", HttpStatus.BAD_REQUEST);
        }

        if (request.getMinHumidity() < 0.0 || request.getMaxHumidity() > 100.0) {
            throw new CustomException("Humidity boundaries must reside between 0% and 100%.", HttpStatus.BAD_REQUEST);
        }

        if (request.getMaxNh3() < 0.0) {
            throw new CustomException("Maximum NH3 threshold cannot be negative.", HttpStatus.BAD_REQUEST);
        }

        if (request.getMaxLpg() < 0.0) {
            throw new CustomException("Maximum LPG reference value cannot be negative.", HttpStatus.BAD_REQUEST);
        }
    }

    // High performance manual mapping
    private ThresholdResponse mapToThresholdResponse(Threshold t) {
        Long age = null;
        if (t.getPlacementDate() != null) {
            age = java.time.temporal.ChronoUnit.DAYS.between(t.getPlacementDate(), java.time.LocalDate.now());
        }

        return ThresholdResponse.builder()
                .id(t.getId())
                .minTemperature(t.getMinTemperature())
                .maxTemperature(t.getMaxTemperature())
                .minHumidity(t.getMinHumidity())
                .maxHumidity(t.getMaxHumidity())
                .maxNh3(t.getMaxNh3())
                .maxLpg(t.getMaxLpg())
                .zoneId(t.getZone().getId())
                .updatedAt(t.getUpdatedAt())
                .autoThresholdEnabled(t.isAutoThresholdEnabled())
                .placementDate(t.getPlacementDate())
                .manualOverrideEnabled(t.isManualOverrideEnabled())
                .chickAge(age != null ? (int) (long) age : null)
                .activeMinTemperature(t.getEffectiveMinTemperature())
                .activeMaxTemperature(t.getEffectiveMaxTemperature())
                .activeMinHumidity(t.getEffectiveMinHumidity())
                .activeMaxHumidity(t.getEffectiveMaxHumidity())
                .build();
    }

    private GrowthScheduleStageResponse mapToGrowthScheduleStageResponse(GrowthScheduleStage stage) {
        return GrowthScheduleStageResponse.builder()
                .id(stage.getId())
                .startDay(stage.getStartDay())
                .endDay(stage.getEndDay())
                .minTemperature(stage.getMinTemperature())
                .maxTemperature(stage.getMaxTemperature())
                .minHumidity(stage.getMinHumidity())
                .maxHumidity(stage.getMaxHumidity())
                .build();
    }
}
