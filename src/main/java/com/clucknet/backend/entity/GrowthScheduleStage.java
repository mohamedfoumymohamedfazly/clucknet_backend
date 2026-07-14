package com.clucknet.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "growth_schedule_stages", indexes = {
    @Index(name = "idx_growth_schedule_zone_id", columnList = "zone_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrowthScheduleStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_day", nullable = false)
    private Integer startDay;

    @Column(name = "end_day", nullable = false)
    private Integer endDay;

    @Column(name = "min_temperature", nullable = false)
    private Double minTemperature;

    @Column(name = "max_temperature", nullable = false)
    private Double maxTemperature;

    @Column(name = "min_humidity", nullable = false)
    private Double minHumidity;

    @Column(name = "max_humidity", nullable = false)
    private Double maxHumidity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;
}
