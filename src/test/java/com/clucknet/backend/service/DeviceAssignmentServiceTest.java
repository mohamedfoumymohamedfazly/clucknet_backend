package com.clucknet.backend.service;

import com.clucknet.backend.dto.response.DeviceResponse;
import com.clucknet.backend.entity.Device;
import com.clucknet.backend.entity.DeviceStatus;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.exception.CustomException;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.repository.DeviceRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.service.impl.DeviceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeviceAssignmentServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ZoneRepository zoneRepository;

    private DeviceServiceImpl deviceService;

    private Zone zoneA;
    private Zone zoneB;
    private Device unassignedDevice;
    private Device assignedDevice;

    @BeforeEach
    public void setUp() {
        deviceService = new DeviceServiceImpl(deviceRepository, zoneRepository);

        zoneA = Zone.builder().id(1L).name("Zone A").createdAt(LocalDateTime.now()).build();
        zoneB = Zone.builder().id(2L).name("Zone B").createdAt(LocalDateTime.now()).build();

        unassignedDevice = Device.builder()
                .id("AA:BB:CC:DD:EE:FF")
                .name("Unassigned Node")
                .status(DeviceStatus.OFFLINE)
                .zone(null)
                .createdAt(LocalDateTime.now())
                .build();

        assignedDevice = Device.builder()
                .id("11:22:33:44:55:66")
                .name("Assigned Node")
                .status(DeviceStatus.ONLINE)
                .zone(zoneA)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    public void getDevicesByZoneId_Success() {
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zoneA));
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.of(assignedDevice));

        List<DeviceResponse> results = deviceService.getDevicesByZoneId(1L);

        assertEquals(1, results.size());
        assertEquals("11:22:33:44:55:66", results.get(0).getId());
        assertEquals(1L, results.get(0).getZoneId());
    }

    @Test
    public void getDevicesByZoneId_ZoneNotFound() {
        when(zoneRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            deviceService.getDevicesByZoneId(99L);
        });
    }

    @Test
    public void getUnassignedDevices_Success() {
        when(deviceRepository.findByZoneIsNull()).thenReturn(List.of(unassignedDevice));

        List<DeviceResponse> results = deviceService.getUnassignedDevices();

        assertEquals(1, results.size());
        assertEquals("AA:BB:CC:DD:EE:FF", results.get(0).getId());
        assertNull(results.get(0).getZoneId());
    }

    @Test
    public void assignDeviceToZone_Success() {
        when(deviceRepository.findById("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(unassignedDevice));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zoneA));
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.assignDeviceToZone("AA:BB:CC:DD:EE:FF", 1L);

        assertNotNull(response);
        assertEquals(1L, response.getZoneId());
        verify(deviceRepository, times(1)).save(unassignedDevice);
    }

    @Test
    public void assignDeviceToZone_DeviceAlreadyAssigned() {
        when(deviceRepository.findById("11:22:33:44:55:66")).thenReturn(Optional.of(assignedDevice));
        when(zoneRepository.findById(2L)).thenReturn(Optional.of(zoneB));

        CustomException exception = assertThrows(CustomException.class, () -> {
            deviceService.assignDeviceToZone("11:22:33:44:55:66", 2L);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("already assigned to a zone"));
    }

    @Test
    public void assignDeviceToZone_ZoneAlreadyOccupied() {
        when(deviceRepository.findById("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(unassignedDevice));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zoneA));
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.of(assignedDevice));

        CustomException exception = assertThrows(CustomException.class, () -> {
            deviceService.assignDeviceToZone("AA:BB:CC:DD:EE:FF", 1L);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("already associated with device"));
    }

    @Test
    public void updateDeviceAssignment_Success() {
        when(deviceRepository.findById("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(unassignedDevice));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zoneA));
        when(deviceRepository.findByZoneId(1L)).thenReturn(Optional.of(assignedDevice));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.updateDeviceAssignment("AA:BB:CC:DD:EE:FF", 1L);

        assertNotNull(response);
        assertEquals(1L, response.getZoneId());
        // Verify that the conflicting device (assignedDevice) is dissociated
        assertNull(assignedDevice.getZone());
        verify(deviceRepository, times(1)).saveAndFlush(assignedDevice);
        verify(deviceRepository, times(1)).save(unassignedDevice);
    }

    @Test
    public void removeDeviceAssignment_Success() {
        when(deviceRepository.findById("11:22:33:44:55:66")).thenReturn(Optional.of(assignedDevice));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zoneA));

        deviceService.removeDeviceAssignment("11:22:33:44:55:66", 1L);

        assertNull(assignedDevice.getZone());
        verify(deviceRepository, times(1)).save(assignedDevice);
    }

    @Test
    public void removeDeviceAssignment_MismatchedZone() {
        when(deviceRepository.findById("11:22:33:44:55:66")).thenReturn(Optional.of(assignedDevice));
        when(zoneRepository.findById(2L)).thenReturn(Optional.of(zoneB));

        CustomException exception = assertThrows(CustomException.class, () -> {
            deviceService.removeDeviceAssignment("11:22:33:44:55:66", 2L);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("not assigned to the specified zone"));
    }
}
