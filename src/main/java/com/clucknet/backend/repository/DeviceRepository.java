package com.clucknet.backend.repository;

import com.clucknet.backend.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String> {

    @Query("SELECT d FROM Device d LEFT JOIN FETCH d.zone WHERE d.id = :id")
    Optional<Device> findByIdWithZone(@Param("id") String id);

    Optional<Device> findByZoneId(Long zoneId);

    List<Device> findByZoneIsNull();

    long countByStatus(com.clucknet.backend.entity.DeviceStatus status);
}
