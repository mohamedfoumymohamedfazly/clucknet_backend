package com.clucknet.backend.controller;

import com.clucknet.backend.dto.request.ZoneCreateRequest;
import com.clucknet.backend.dto.response.ApiResponse;
import com.clucknet.backend.dto.response.LiveTelemetryResponse;
import com.clucknet.backend.dto.response.TelemetryDataResponse;
import com.clucknet.backend.dto.response.ZoneResponse;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.dto.request.DeviceAssignmentRequest;
import com.clucknet.backend.dto.response.DeviceResponse;
import com.clucknet.backend.security.role.AuthorityConstants;
import com.clucknet.backend.service.DeviceService;
import com.clucknet.backend.service.TelemetryService;
import com.clucknet.backend.service.ZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.clucknet.backend.repository.AlertRepository;
import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.AlertType;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zoneService;
    private final TelemetryService telemetryService;
    private final DeviceService deviceService;
    private final AlertRepository alertRepository;

    public ZoneController(ZoneService zoneService, TelemetryService telemetryService, DeviceService deviceService, AlertRepository alertRepository) {
        this.zoneService = zoneService;
        this.telemetryService = telemetryService;
        this.deviceService = deviceService;
        this.alertRepository = alertRepository;
    }

    @PostMapping
    @PreAuthorize(AuthorityConstants.HAS_OWNER) // Only OWNER can create zones
    public ResponseEntity<ApiResponse<ZoneResponse>> createZone(@Valid @RequestBody ZoneCreateRequest request) {
        ZoneResponse response = zoneService.createZone(request);
        return new ResponseEntity<>(ApiResponse.success("Climate zone created successfully.", response), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER) // Both OWNER and FARMER can view dashboards
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> getAllZones() {
        List<ZoneResponse> response = zoneService.getAllZones();
        return ResponseEntity.ok(ApiResponse.success("Zones retrieved successfully.", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<ZoneResponse>> getZoneById(@PathVariable Long id) {
        ZoneResponse response = zoneService.getZoneById(id);
        return ResponseEntity.ok(ApiResponse.success("Zone details retrieved successfully.", response));
    }

    @GetMapping("/{id}/live")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<LiveTelemetryResponse>> getLiveTelemetry(@PathVariable Long id) {
        // Validate that zone exists (will throw ResourceNotFoundException if it doesn't)
        zoneService.getZoneById(id);

        TelemetryDataResponse latest = telemetryService.getLatestTelemetry(id);
        if (latest == null) {
            throw new ResourceNotFoundException("Telemetry", "zoneId", id);
        }

        Double lpg = latest.getLpg();
        Instant timestamp = latest.getTimestamp();

        // Check if there is an LPG alert newer than this telemetry point
        Optional<Alert> latestLpgAlertOpt = alertRepository.findFirstByZoneIdAndTypeOrderByCreatedAtDesc(id, AlertType.LPG_DANGER);
        if (latestLpgAlertOpt.isPresent()) {
            Alert alert = latestLpgAlertOpt.get();
            Instant alertTime = alert.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
            if (alertTime.isAfter(timestamp)) {
                lpg = alert.getTriggeredValue();
                timestamp = alertTime;
            }
        }

        LiveTelemetryResponse response = LiveTelemetryResponse.builder()
                .temperature(latest.getTemperature())
                .humidity(latest.getHumidity())
                .nh3(latest.getNh3())
                .lpg(lpg)
                .timestamp(timestamp)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Live telemetry readings retrieved successfully.", response));
    }

    @GetMapping("/{zoneId}/devices")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getDevicesAssignedToZone(@PathVariable Long zoneId) {
        List<DeviceResponse> response = deviceService.getDevicesByZoneId(zoneId);
        return ResponseEntity.ok(ApiResponse.success("Devices assigned to zone retrieved successfully.", response));
    }

    @PostMapping("/{zoneId}/devices")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<DeviceResponse>> assignDeviceToZone(
            @PathVariable Long zoneId,
            @Valid @RequestBody DeviceAssignmentRequest request) {
        DeviceResponse response = deviceService.assignDeviceToZone(request.getDeviceId(), zoneId);
        return ResponseEntity.ok(ApiResponse.success("Device assigned to zone successfully.", response));
    }

    @PutMapping("/{zoneId}/devices/{deviceId}")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDeviceAssignment(
            @PathVariable Long zoneId,
            @PathVariable String deviceId) {
        DeviceResponse response = deviceService.updateDeviceAssignment(deviceId, zoneId);
        return ResponseEntity.ok(ApiResponse.success("Device assignment updated successfully.", response));
    }

    @DeleteMapping("/{zoneId}/devices/{deviceId}")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<Void>> removeDeviceAssignment(
            @PathVariable Long zoneId,
            @PathVariable String deviceId) {
        deviceService.removeDeviceAssignment(deviceId, zoneId);
        return ResponseEntity.ok(ApiResponse.success("Device assignment removed successfully."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AuthorityConstants.HAS_OWNER) // Only OWNER can delete zones
    public ResponseEntity<ApiResponse<Void>> deleteZone(@PathVariable Long id) {
        zoneService.deleteZone(id);
        return ResponseEntity.ok(ApiResponse.success("Zone deleted successfully. Associated thresholds and devices detached."));
    }
}
