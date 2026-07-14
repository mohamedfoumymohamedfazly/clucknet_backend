package com.clucknet.backend.service;

import com.clucknet.backend.dto.request.ZoneCreateRequest;
import com.clucknet.backend.dto.response.ZoneResponse;

import java.util.List;

public interface ZoneService {

    ZoneResponse createZone(ZoneCreateRequest request);

    List<ZoneResponse> getAllZones();

    ZoneResponse getZoneById(Long id);

    void deleteZone(Long id);
}
