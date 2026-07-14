package com.clucknet.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThresholdResponse {

    private Long id;
    private Double minTemperature;
    private Double maxTemperature;
    private Double minHumidity;
    private Double maxHumidity;
    private Double maxNh3;
    private Double maxLpg;
    private Long zoneId;
    private LocalDateTime updatedAt;
    private Boolean autoThresholdEnabled;
    private java.time.LocalDate placementDate;
    private Boolean manualOverrideEnabled;
    private Integer chickAge;
    private Double activeMinTemperature;
    private Double activeMaxTemperature;
    private Double activeMinHumidity;
    private Double activeMaxHumidity;
}
