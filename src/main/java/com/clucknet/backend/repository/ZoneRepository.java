package com.clucknet.backend.repository;

import com.clucknet.backend.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    boolean existsByName(String name);

    // Eagerly loads all zones along with their linked devices and thresholds in a single DB join operation
    @Query("SELECT DISTINCT z FROM Zone z LEFT JOIN FETCH z.device LEFT JOIN FETCH z.threshold ORDER BY z.name ASC")
    List<Zone> findAllWithDeviceAndThreshold();

    @Query("SELECT z FROM Zone z LEFT JOIN FETCH z.device LEFT JOIN FETCH z.threshold WHERE z.id = :id")
    Optional<Zone> findByIdWithDeviceAndThreshold(Long id);
}
