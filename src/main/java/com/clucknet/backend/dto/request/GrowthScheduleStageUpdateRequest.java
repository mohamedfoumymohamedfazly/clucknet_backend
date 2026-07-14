package com.clucknet.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrowthScheduleStageUpdateRequest {
    private Long id;

    @NotNull(message = "Start day is required.")
    private Integer startDay;

    @NotNull(message = "End day is required.")
    private Integer endDay;

    @NotNull(message = "Minimum temperature is required.")
    private Double minTemperature;

    @NotNull(message = "Maximum temperature is required.")
    private Double maxTemperature;

    @NotNull(message = "Minimum humidity is required.")
    private Double minHumidity;

    @NotNull(message = "Maximum humidity is required.")
    private Double maxHumidity;
}
