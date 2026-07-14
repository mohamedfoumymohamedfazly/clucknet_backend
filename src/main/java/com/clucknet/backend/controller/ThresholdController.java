package com.clucknet.backend.controller;

import com.clucknet.backend.dto.request.ThresholdUpdateRequest;
import com.clucknet.backend.dto.response.ApiResponse;
import com.clucknet.backend.dto.response.ThresholdResponse;
import com.clucknet.backend.security.role.AuthorityConstants;
import com.clucknet.backend.service.ThresholdService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/thresholds")
public class ThresholdController {

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    @PutMapping("/{zoneId}")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<ThresholdResponse>> updateThreshold(
            @PathVariable Long zoneId,
            @Valid @RequestBody ThresholdUpdateRequest request) {
        ThresholdResponse response = thresholdService.updateThreshold(zoneId, request);
        return ResponseEntity.ok(ApiResponse.success("Climate thresholds updated successfully and dispatched to IoT gateways.", response));
    }

    @GetMapping("/{zoneId}")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER) // Both OWNER and FARMER can view current thresholds
    public ResponseEntity<ApiResponse<ThresholdResponse>> getThresholdByZoneId(@PathVariable Long zoneId) {
        ThresholdResponse response = thresholdService.getThresholdByZoneId(zoneId);
        return ResponseEntity.ok(ApiResponse.success("Climate thresholds retrieved successfully.", response));
    }

    @GetMapping("/{zoneId}/schedule")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<java.util.List<com.clucknet.backend.dto.response.GrowthScheduleStageResponse>>> getScheduleStages(
            @PathVariable Long zoneId) {
        java.util.List<com.clucknet.backend.dto.response.GrowthScheduleStageResponse> response = thresholdService.getScheduleStages(zoneId);
        return ResponseEntity.ok(ApiResponse.success("Growth schedule stages retrieved successfully.", response));
    }

    @PutMapping("/{zoneId}/schedule")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<java.util.List<com.clucknet.backend.dto.response.GrowthScheduleStageResponse>>> updateScheduleStages(
            @PathVariable Long zoneId,
            @Valid @RequestBody java.util.List<com.clucknet.backend.dto.request.GrowthScheduleStageUpdateRequest> requests) {
        java.util.List<com.clucknet.backend.dto.response.GrowthScheduleStageResponse> response = thresholdService.updateScheduleStages(zoneId, requests);
        return ResponseEntity.ok(ApiResponse.success("Growth schedule stages updated successfully.", response));
    }
}
