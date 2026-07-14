package com.clucknet.backend.service;

import com.clucknet.backend.dto.request.DeviceRegisterRequest;
import com.clucknet.backend.dto.request.DeviceUpdateRequest;
import com.clucknet.backend.dto.response.DeviceResponse;

import java.util.List;

public interface DeviceService {

    DeviceResponse registerDevice(DeviceRegisterRequest request);

    DeviceResponse getDeviceById(String id);

    DeviceResponse associateDeviceToZone(String id, Long zoneId);

    List<DeviceResponse> getAllDevices();

    void deleteDevice(String id);

    List<DeviceResponse> getDevicesByZoneId(Long zoneId);

    List<DeviceResponse> getUnassignedDevices();

    DeviceResponse assignDeviceToZone(String deviceId, Long zoneId);

    DeviceResponse updateDeviceAssignment(String deviceId, Long zoneId);

    void removeDeviceAssignment(String deviceId, Long zoneId);

    DeviceResponse reassignDevice(String deviceId, Long zoneId);

    DeviceResponse updateDevice(String id, DeviceUpdateRequest request);
}
