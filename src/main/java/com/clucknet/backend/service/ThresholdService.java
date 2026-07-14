package com.clucknet.backend.service;

import com.clucknet.backend.dto.request.ThresholdUpdateRequest;
import com.clucknet.backend.dto.response.ThresholdResponse;

public interface ThresholdService {

    ThresholdResponse updateThreshold(Long zoneId, ThresholdUpdateRequest request);

    ThresholdResponse getThresholdByZoneId(Long zoneId);

    java.util.List<com.clucknet.backend.dto.response.GrowthScheduleStageResponse> getScheduleStages(Long zoneId);

    java.util.List<com.clucknet.backend.dto.response.GrowthScheduleStageResponse> updateScheduleStages(
            Long zoneId,
            java.util.List<com.clucknet.backend.dto.request.GrowthScheduleStageUpdateRequest> requests);
}
