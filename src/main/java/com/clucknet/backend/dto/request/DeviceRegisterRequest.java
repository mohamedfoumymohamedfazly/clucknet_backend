package com.clucknet.backend.dto.request;

import com.clucknet.backend.validation.MacAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceRegisterRequest {

    @NotBlank(message = "Device physical address (MAC) is required.")
    @MacAddress // Custom validation constraints
    private String id;

    @NotBlank(message = "Device name is required.")
    @Size(min = 2, max = 100, message = "Device name must be between 2 and 100 characters.")
    private String name;

    private Long zoneId; // Optional zone linkage during registration
}
