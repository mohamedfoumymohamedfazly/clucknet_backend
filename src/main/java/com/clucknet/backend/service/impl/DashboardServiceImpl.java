package com.clucknet.backend.service.impl;

import com.clucknet.backend.dto.response.DashboardSummaryResponse;
import com.clucknet.backend.entity.AlertStatus;
import com.clucknet.backend.entity.DeviceStatus;
import com.clucknet.backend.entity.Severity;
import com.clucknet.backend.repository.AlertRepository;
import com.clucknet.backend.repository.DeviceRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final ZoneRepository zoneRepository;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;

    public DashboardServiceImpl(ZoneRepository zoneRepository,
                                DeviceRepository deviceRepository,
                                AlertRepository alertRepository) {
        this.zoneRepository = zoneRepository;
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary() {
        long totalZones = zoneRepository.count();
        long totalDevices = deviceRepository.count();
        long onlineDevices = deviceRepository.countByStatus(DeviceStatus.ONLINE);
        long offlineDevices = deviceRepository.countByStatus(DeviceStatus.OFFLINE);
        long activeAlerts = alertRepository.countByStatus(AlertStatus.ACTIVE);
        long criticalAlerts = alertRepository.countByStatusAndSeverity(AlertStatus.ACTIVE, Severity.CRITICAL);

        return DashboardSummaryResponse.builder()
                .totalZones(totalZones)
                .totalDevices(totalDevices)
                .onlineDevices(onlineDevices)
                .offlineDevices(offlineDevices)
                .activeAlerts(activeAlerts)
                .criticalAlerts(criticalAlerts)
                .build();
    }
}
