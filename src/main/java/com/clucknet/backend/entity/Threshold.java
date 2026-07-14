package com.clucknet.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "thresholds", indexes = {
    @Index(name = "idx_thresholds_zone_id", columnList = "zone_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Threshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_temperature", nullable = false)
    private Double minTemperature;

    @Column(name = "max_temperature", nullable = false)
    private Double maxTemperature;

    @Column(name = "min_humidity", nullable = false)
    private Double minHumidity;

    @Column(name = "max_humidity", nullable = false)
    private Double maxHumidity;

    @Column(name = "max_nh3", nullable = false)
    private Double maxNh3;

    @Column(name = "max_lpg", nullable = false)
    private Double maxLpg; // Edge threshold configuration reference for LPG

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", referencedColumnName = "id", nullable = false, unique = true)
    private Zone zone;

    @Column(name = "auto_threshold_enabled", nullable = false)
    @Builder.Default
    private boolean autoThresholdEnabled = false;

    @Column(name = "placement_date")
    private java.time.LocalDate placementDate;

    @Column(name = "manual_override_enabled", nullable = false)
    @Builder.Default
    private boolean manualOverrideEnabled = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Double getEffectiveMinTemperature() {
        if (autoThresholdEnabled && !manualOverrideEnabled && placementDate != null) {
            long age = java.time.temporal.ChronoUnit.DAYS.between(placementDate, java.time.LocalDate.now());
            return getStageForAge(age)
                    .map(GrowthScheduleStage::getMinTemperature)
                    .orElse(minTemperature);
        }
        return minTemperature;
    }

    public Double getEffectiveMaxTemperature() {
        if (autoThresholdEnabled && !manualOverrideEnabled && placementDate != null) {
            long age = java.time.temporal.ChronoUnit.DAYS.between(placementDate, java.time.LocalDate.now());
            return getStageForAge(age)
                    .map(GrowthScheduleStage::getMaxTemperature)
                    .orElse(maxTemperature);
        }
        return maxTemperature;
    }

    public Double getEffectiveMinHumidity() {
        if (autoThresholdEnabled && !manualOverrideEnabled && placementDate != null) {
            long age = java.time.temporal.ChronoUnit.DAYS.between(placementDate, java.time.LocalDate.now());
            return getStageForAge(age)
                    .map(GrowthScheduleStage::getMinHumidity)
                    .orElse(minHumidity);
        }
        return minHumidity;
    }

    public Double getEffectiveMaxHumidity() {
        if (autoThresholdEnabled && !manualOverrideEnabled && placementDate != null) {
            long age = java.time.temporal.ChronoUnit.DAYS.between(placementDate, java.time.LocalDate.now());
            return getStageForAge(age)
                    .map(GrowthScheduleStage::getMaxHumidity)
                    .orElse(maxHumidity);
        }
        return maxHumidity;
    }

    private java.util.Optional<GrowthScheduleStage> getStageForAge(long age) {
        if (zone == null || zone.getGrowthStages() == null) {
            return java.util.Optional.empty();
        }
        return zone.getGrowthStages().stream()
                .filter(s -> age >= s.getStartDay() && age <= s.getEndDay())
                .findFirst();
    }
}
