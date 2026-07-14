package com.clucknet.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAssignmentRequest {

    @NotBlank(message = "Device ID is required.")
    private String deviceId;
}
