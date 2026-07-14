package com.clucknet.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ZoneCreateRequest {

    @NotBlank(message = "Zone name must not be empty.")
    @Size(min = 2, max = 100, message = "Zone name must be between 2 and 100 characters.")
    private String name;
}
