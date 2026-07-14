package com.clucknet.backend.service.impl;

import com.clucknet.backend.dto.request.ZoneCreateRequest;
import com.clucknet.backend.dto.response.ThresholdResponse;
import com.clucknet.backend.dto.response.ZoneResponse;
import com.clucknet.backend.entity.Threshold;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.exception.CustomException;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.repository.ThresholdRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.service.ZoneService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZoneServiceImpl implements ZoneService {

    private final ZoneRepository zoneRepository;
    private final ThresholdRepository thresholdRepository;

    public ZoneServiceImpl(ZoneRepository zoneRepository, ThresholdRepository thresholdRepository) {
        this.zoneRepository = zoneRepository;
        this.thresholdRepository = thresholdRepository;
    }

    @Override
    @Transactional
    public ZoneResponse createZone(ZoneCreateRequest request) {
        if (zoneRepository.existsByName(request.getName())) {
            throw new CustomException("Zone name is already in use.", HttpStatus.BAD_REQUEST);
        }

        // 1. Persist the new Zone
        Zone zone = Zone.builder()
                .name(request.getName())
                .build();
        Zone savedZone = zoneRepository.save(zone);

        // 2. Initialize default safe operational thresholds to prevent null checking errors
        Threshold defaultThreshold = Threshold.builder()
                .minTemperature(15.0) // Safe minimum temp
                .maxTemperature(35.0) // Safe maximum temp
                .minHumidity(40.0)    // Safe minimum humidity
                .maxHumidity(80.0)    // Safe maximum humidity
                .maxNh3(250.0)        // Safe NH3 threshold in ppm
                .maxLpg(300.0)        // Default reference LPG danger value
                .zone(savedZone)
                .build();
        thresholdRepository.save(defaultThreshold);

        savedZone.setThreshold(defaultThreshold);

        return mapToZoneResponse(savedZone);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> getAllZones() {
        // Fetch all zones using single query FETCH optimization
        return zoneRepository.findAllWithDeviceAndThreshold().stream()
                .map(this::mapToZoneResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneResponse getZoneById(Long id) {
        Zone zone = zoneRepository.findByIdWithDeviceAndThreshold(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", id));
        return mapToZoneResponse(zone);
    }

    @Override
    @Transactional
    public void deleteZone(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", id));
        
        // Due to CascadeType.ALL on Device and Threshold, deleting zone automatically updates/clears children
        zoneRepository.delete(zone);
    }

    // Manual High Performance Entity to DTO Mapper
    private ZoneResponse mapToZoneResponse(Zone zone) {
        ZoneResponse.ZoneResponseBuilder builder = ZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .createdAt(zone.getCreatedAt());

        if (zone.getDevice() != null) {
            builder.deviceId(zone.getDevice().getId())
                   .deviceName(zone.getDevice().getName())
                   .deviceStatus(zone.getDevice().getStatus().name());
        }

        if (zone.getThreshold() != null) {
            Threshold t = zone.getThreshold();
            Long age = null;
            if (t.getPlacementDate() != null) {
                age = java.time.temporal.ChronoUnit.DAYS.between(t.getPlacementDate(), java.time.LocalDate.now());
            }
            builder.threshold(ThresholdResponse.builder()
                    .id(t.getId())
                    .minTemperature(t.getMinTemperature())
                    .maxTemperature(t.getMaxTemperature())
                    .minHumidity(t.getMinHumidity())
                    .maxHumidity(t.getMaxHumidity())
                    .maxNh3(t.getMaxNh3())
                    .maxLpg(t.getMaxLpg())
                    .zoneId(zone.getId())
                    .updatedAt(t.getUpdatedAt())
                    .autoThresholdEnabled(t.isAutoThresholdEnabled())
                    .placementDate(t.getPlacementDate())
                    .manualOverrideEnabled(t.isManualOverrideEnabled())
                    .chickAge(age != null ? (int) (long) age : null)
                    .activeMinTemperature(t.getEffectiveMinTemperature())
                    .activeMaxTemperature(t.getEffectiveMaxTemperature())
                    .activeMinHumidity(t.getEffectiveMinHumidity())
                    .activeMaxHumidity(t.getEffectiveMaxHumidity())
                    .build());
        }

        return builder.build();
    }
}
