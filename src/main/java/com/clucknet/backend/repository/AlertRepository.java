package com.clucknet.backend.repository;

import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Fetch alerts by Zone with Pagination
    Page<Alert> findByZoneIdOrderByCreatedAtDesc(Long zoneId, Pageable pageable);

    // Fetch all alerts with Pagination
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Fetch active alerts for a specific zone and type to prevent duplicate active alerts
    List<Alert> findByZoneIdAndTypeAndStatus(Long zoneId, com.clucknet.backend.entity.AlertType type, AlertStatus status);

    Optional<Alert> findFirstByZoneIdAndTypeOrderByCreatedAtDesc(Long zoneId, com.clucknet.backend.entity.AlertType type);

    // Fetch active alerts for a device
    List<Alert> findByDeviceIdAndStatus(String deviceId, AlertStatus status);

    // High performance query to fetch details in one go
    @Query("SELECT a FROM Alert a JOIN FETCH a.zone JOIN FETCH a.device WHERE a.status = :status ORDER BY a.createdAt DESC")
    List<Alert> findAllActiveAlertsWithDetails(@Param("status") AlertStatus status);

    long countByStatus(AlertStatus status);

    long countByStatusAndSeverity(AlertStatus status, com.clucknet.backend.entity.Severity severity);

    List<Alert> findByZoneIdOrderByCreatedAtDesc(Long zoneId);

    List<Alert> findAllByOrderByCreatedAtDesc();
}
