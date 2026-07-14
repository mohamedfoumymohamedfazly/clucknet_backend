package com.clucknet.backend.repository;

import com.clucknet.backend.entity.GrowthScheduleStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrowthScheduleStageRepository extends JpaRepository<GrowthScheduleStage, Long> {
    List<GrowthScheduleStage> findByZoneIdOrderByStartDayAsc(Long zoneId);
    void deleteByZoneId(Long zoneId);
}
