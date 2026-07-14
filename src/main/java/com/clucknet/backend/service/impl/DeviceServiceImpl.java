package com.clucknet.backend.service.impl;

import com.clucknet.backend.dto.request.DeviceRegisterRequest;
import com.clucknet.backend.dto.request.DeviceUpdateRequest;
import com.clucknet.backend.dto.response.DeviceResponse;
import com.clucknet.backend.entity.Device;
import com.clucknet.backend.entity.DeviceStatus;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.exception.CustomException;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.repository.DeviceRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.service.DeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;

    public DeviceServiceImpl(DeviceRepository deviceRepository, ZoneRepository zoneRepository) {
        this.deviceRepository = deviceRepository;
        this.zoneRepository = zoneRepository;
    }

    @Override
    @Transactional
    public DeviceResponse registerDevice(DeviceRegisterRequest request) {
        if (deviceRepository.existsById(request.getId())) {
            throw new CustomException("Device physical address (MAC) is already registered.", HttpStatus.BAD_REQUEST);
        }

        Zone zone = null;
        if (request.getZoneId() != null) {
            zone = zoneRepository.findById(request.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", request.getZoneId()));
            
            // Check if the Zone already has a device linked to satisfy 1:1 constraints
            Optional<Device> existingDevice = deviceRepository.findByZoneId(request.getZoneId());
            if (existingDevice.isPresent()) {
                throw new CustomException("Selected Zone is already associated with device ID: " + existingDevice.get().getId(), HttpStatus.BAD_REQUEST);
            }
        }

        Device device = Device.builder()
                .id(request.getId())
                .name(request.getName())
                .status(DeviceStatus.OFFLINE) // Registered devices start offline until telemetry pings
                .zone(zone)
                .build();

        Device savedDevice = deviceRepository.save(device);
        return mapToDeviceResponse(savedDevice);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceResponse getDeviceById(String id) {
        Device device = deviceRepository.findByIdWithZone(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", id));
        return mapToDeviceResponse(device);
    }

    @Override
    @Transactional
    public DeviceResponse associateDeviceToZone(String id, Long zoneId) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", id));

        Zone zone = null;
        if (zoneId != null) {
            zone = zoneRepository.findById(zoneId)
                    .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

            // Check if another device is currently linked to this zone
            Optional<Device> conflictingDevice = deviceRepository.findByZoneId(zoneId);
            if (conflictingDevice.isPresent() && !conflictingDevice.get().getId().equals(id)) {
                throw new CustomException("Target Zone is already linked to active device ID: " + conflictingDevice.get().getId(), HttpStatus.BAD_REQUEST);
            }
        }

        device.setZone(zone);
        Device updatedDevice = deviceRepository.save(device);
        return mapToDeviceResponse(updatedDevice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices() {
        return deviceRepository.findAll().stream()
                .map(this::mapToDeviceResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDevice(String id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", id));
        
        // Remove device. Note that if associated with a zone, it will dissociate it safely.
        deviceRepository.delete(device);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevicesByZoneId(Long zoneId) {
        zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        return deviceRepository.findByZoneId(zoneId).stream()
                .map(this::mapToDeviceResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> getUnassignedDevices() {
        return deviceRepository.findByZoneIsNull().stream()
                .map(this::mapToDeviceResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DeviceResponse assignDeviceToZone(String deviceId, Long zoneId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", deviceId));

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        // Prevent duplicate assignments: check if device is already assigned to a zone
        if (device.getZone() != null) {
            throw new CustomException("Device is already assigned to a zone: " + device.getZone().getName(), HttpStatus.BAD_REQUEST);
        }

        // Check if the zone is already associated with another device
        Optional<Device> conflictingDevice = deviceRepository.findByZoneId(zoneId);
        if (conflictingDevice.isPresent()) {
            throw new CustomException("Zone is already associated with device ID: " + conflictingDevice.get().getId(), HttpStatus.BAD_REQUEST);
        }

        device.setZone(zone);
        Device updatedDevice = deviceRepository.save(device);
        return mapToDeviceResponse(updatedDevice);
    }

    @Override
    @Transactional
    public DeviceResponse updateDeviceAssignment(String deviceId, Long zoneId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", deviceId));

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        // If already correctly assigned, just return
        if (device.getZone() != null && device.getZone().getId().equals(zoneId)) {
            return mapToDeviceResponse(device);
        }

        // Dissociate this device from its current zone first
        if (device.getZone() != null) {
            device.setZone(null);
            deviceRepository.saveAndFlush(device);
        }

        // Dissociate any other device currently assigned to the target zone
        Optional<Device> conflictingDevice = deviceRepository.findByZoneId(zoneId);
        if (conflictingDevice.isPresent()) {
            Device conflict = conflictingDevice.get();
            conflict.setZone(null);
            deviceRepository.saveAndFlush(conflict);
        }

        device.setZone(zone);
        Device updatedDevice = deviceRepository.save(device);
        return mapToDeviceResponse(updatedDevice);
    }

    @Override
    @Transactional
    public void removeDeviceAssignment(String deviceId, Long zoneId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", deviceId));

        zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", "id", zoneId));

        if (device.getZone() == null || !device.getZone().getId().equals(zoneId)) {
            throw new CustomException("Device is not assigned to the specified zone.", HttpStatus.BAD_REQUEST);
        }

        device.setZone(null);
        deviceRepository.save(device);
    }

    @Override
    @Transactional
    public DeviceResponse reassignDevice(String deviceId, Long zoneId) {
        return updateDeviceAssignment(deviceId, zoneId);
    }

    // Manual High Performance Entity to DTO Mapper
    private DeviceResponse mapToDeviceResponse(Device device) {
        DeviceResponse.DeviceResponseBuilder builder = DeviceResponse.builder()
                .id(device.getId())
                .name(device.getName())
                .status(device.getStatus())
                .createdAt(device.getCreatedAt());

        if (device.getZone() != null) {
            builder.zoneId(device.getZone().getId())
                   .zoneName(device.getZone().getName());
        }

        return builder.build();
    }

    @Override
    @Transactional
    public DeviceResponse updateDevice(String id, DeviceUpdateRequest request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device", "id", id));
        device.setName(request.getName());
        Device updatedDevice = deviceRepository.save(device);
        return mapToDeviceResponse(updatedDevice);
    }
}
