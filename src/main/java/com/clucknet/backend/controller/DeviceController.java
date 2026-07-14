package com.clucknet.backend.controller;

import com.clucknet.backend.dto.request.DeviceRegisterRequest;
import com.clucknet.backend.dto.request.DeviceUpdateRequest;
import com.clucknet.backend.dto.response.ApiResponse;
import com.clucknet.backend.dto.response.DeviceResponse;
import com.clucknet.backend.security.role.AuthorityConstants;
import com.clucknet.backend.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @PreAuthorize(AuthorityConstants.HAS_OWNER) // Only OWNER can register devices
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(@Valid @RequestBody DeviceRegisterRequest request) {
        DeviceResponse response = deviceService.registerDevice(request);
        return new ResponseEntity<>(ApiResponse.success("IoT Device registered successfully.", response), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getAllDevices() {
        List<DeviceResponse> response = deviceService.getAllDevices();
        return ResponseEntity.ok(ApiResponse.success("Devices retrieved successfully.", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<DeviceResponse>> getDeviceById(@PathVariable String id) {
        DeviceResponse response = deviceService.getDeviceById(id);
        return ResponseEntity.ok(ApiResponse.success("Device details retrieved successfully.", response));
    }

    @PutMapping("/{id}/associate")
    @PreAuthorize(AuthorityConstants.HAS_OWNER) // Only OWNER can assign devices to zones
    public ResponseEntity<ApiResponse<DeviceResponse>> associateDeviceToZone(
            @PathVariable String id,
            @RequestParam(required = false) Long zoneId) {
        DeviceResponse response = deviceService.associateDeviceToZone(id, zoneId);
        return ResponseEntity.ok(ApiResponse.success("Device zone linkage updated successfully.", response));
    }

    @GetMapping("/unassigned")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getUnassignedDevices() {
        List<DeviceResponse> response = deviceService.getUnassignedDevices();
        return ResponseEntity.ok(ApiResponse.success("Unassigned devices retrieved successfully.", response));
    }

    @PutMapping("/{deviceId}/reassign")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<DeviceResponse>> reassignDevice(
            @PathVariable String deviceId,
            @RequestParam Long zoneId) {
        DeviceResponse response = deviceService.reassignDevice(deviceId, zoneId);
        return ResponseEntity.ok(ApiResponse.success("Device reassigned successfully.", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AuthorityConstants.HAS_OWNER) // Only OWNER can delete devices
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable String id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.ok(ApiResponse.success("Device deleted successfully. detached from all active zone frameworks."));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AuthorityConstants.HAS_OWNER)
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDevice(
            @PathVariable String id,
            @Valid @RequestBody DeviceUpdateRequest request) {
        DeviceResponse response = deviceService.updateDevice(id, request);
        return ResponseEntity.ok(ApiResponse.success("Device details updated successfully.", response));
    }
}
