package com.clucknet.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {
    private long totalZones;
    private long totalDevices;
    private long onlineDevices;
    private long offlineDevices;
    private long activeAlerts;
    private long criticalAlerts;
}
