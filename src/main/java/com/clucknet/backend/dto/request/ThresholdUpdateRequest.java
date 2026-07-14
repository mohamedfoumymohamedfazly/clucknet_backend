package com.clucknet.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ThresholdUpdateRequest {

    @NotNull(message = "Minimum temperature threshold is required.")
    private Double minTemperature;

    @NotNull(message = "Maximum temperature threshold is required.")
    private Double maxTemperature;

    @NotNull(message = "Minimum humidity threshold is required.")
    private Double minHumidity;

    @NotNull(message = "Maximum humidity threshold is required.")
    private Double maxHumidity;

    @NotNull(message = "Maximum NH3 threshold is required.")
    private Double maxNh3;

    @NotNull(message = "Maximum LPG reference threshold is required.")
    private Double maxLpg;

    @NotNull(message = "Auto threshold enabled field is required.")
    private Boolean autoThresholdEnabled;

    private java.time.LocalDate placementDate;

    @NotNull(message = "Manual override enabled field is required.")
    private Boolean manualOverrideEnabled;
}
