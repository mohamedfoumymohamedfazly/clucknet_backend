package com.clucknet.backend.repository;

import com.clucknet.backend.entity.Threshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThresholdRepository extends JpaRepository<Threshold, Long> {

    Optional<Threshold> findByZoneId(Long zoneId);
}
