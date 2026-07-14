package com.clucknet.backend.controller;

import com.clucknet.backend.dto.response.ApiResponse;
import com.clucknet.backend.dto.response.AlertResponse;
import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.repository.AlertRepository;
import com.clucknet.backend.security.role.AuthorityConstants;
import com.clucknet.backend.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final AlertRepository alertRepository;

    public AlertController(AlertService alertService, AlertRepository alertRepository) {
        this.alertService = alertService;
        this.alertRepository = alertRepository;
    }

    @GetMapping
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getAlerts(@RequestParam(required = false) Long zoneId) {
        List<Alert> alerts;
        if (zoneId != null) {
            alerts = alertRepository.findByZoneIdOrderByCreatedAtDesc(zoneId);
        } else {
            alerts = alertRepository.findAllByOrderByCreatedAtDesc();
        }

        List<AlertResponse> response = alerts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Alerts retrieved successfully.", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<AlertResponse>> getAlertById(@PathVariable Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", id));
        return ResponseEntity.ok(ApiResponse.success("Alert details retrieved successfully.", mapToResponse(alert)));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<AlertResponse>> resolveAlert(@PathVariable Long id) {
        Alert resolvedAlert = alertService.resolveAlert(id);
        return ResponseEntity.ok(ApiResponse.success("Alert resolved successfully.", mapToResponse(resolvedAlert)));
    }

    private AlertResponse mapToResponse(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .zoneId(alert.getZone() != null ? alert.getZone().getId() : null)
                .zoneName(alert.getZone() != null ? alert.getZone().getName() : null)
                .severity(alert.getSeverity())
                .message(alert.getMessage())
                .status(alert.getStatus())
                .createdAt(alert.getCreatedAt())
                .resolvedAt(alert.getResolvedAt())
                .type(alert.getType())
                .triggeredValue(alert.getTriggeredValue())
                .thresholdValue(alert.getThresholdValue())
                .build();
    }
}
