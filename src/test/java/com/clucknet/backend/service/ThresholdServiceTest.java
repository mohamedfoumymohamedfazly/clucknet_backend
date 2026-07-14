package com.clucknet.backend.service;

import com.clucknet.backend.dto.request.ThresholdUpdateRequest;
import com.clucknet.backend.dto.response.ThresholdResponse;
import com.clucknet.backend.entity.Threshold;
import com.clucknet.backend.entity.User;
import com.clucknet.backend.entity.Zone;
import com.clucknet.backend.exception.CustomException;
import com.clucknet.backend.repository.GrowthScheduleStageRepository;
import com.clucknet.backend.repository.ThresholdRepository;
import com.clucknet.backend.repository.UserRepository;
import com.clucknet.backend.repository.ZoneRepository;
import com.clucknet.backend.security.role.Role;
import com.clucknet.backend.service.impl.ThresholdServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ThresholdServiceTest {

    @Mock
    private ThresholdRepository thresholdRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GrowthScheduleStageRepository growthScheduleStageRepository;

    @Mock
    private MqttSyncPublisher mqttSyncPublisher;

    private ThresholdServiceImpl thresholdService;

    private final Long zoneId = 1L;
    private Zone zone;
    private Threshold threshold;
    private ThresholdUpdateRequest updateRequest;

    @BeforeEach
    public void setUp() {
        thresholdService = new ThresholdServiceImpl(
                thresholdRepository,
                zoneRepository,
                userRepository,
                growthScheduleStageRepository,
                mqttSyncPublisher
        );

        zone = Zone.builder()
                .id(zoneId)
                .name("Zone A")
                .build();

        threshold = Threshold.builder()
                .id(100L)
                .zone(zone)
                .minTemperature(20.0)
                .maxTemperature(30.0)
                .minHumidity(40.0)
                .maxHumidity(80.0)
                .maxNh3(20.0)
                .maxLpg(2.0)
                .autoThresholdEnabled(false)
                .manualOverrideEnabled(false)
                .build();

        updateRequest = new ThresholdUpdateRequest();
        updateRequest.setMinTemperature(21.0);
        updateRequest.setMaxTemperature(29.0);
        updateRequest.setMinHumidity(45.0);
        updateRequest.setMaxHumidity(75.0);
        updateRequest.setMaxNh3(18.0);
        updateRequest.setMaxLpg(2.0);
        updateRequest.setAutoThresholdEnabled(false);
        updateRequest.setManualOverrideEnabled(false);
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String username, Role role, List<Zone> assignedZones) {
        User user = User.builder()
                .id(1L)
                .username(username)
                .role(role)
                .assignedZones(assignedZones)
                .build();

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
    }

    @Test
    public void whenFarmerNotAssigned_andReadingThreshold_thenAllowAccess() {
        // Arrange
        setupSecurityContext("farmerA", Role.FARMER, new ArrayList<>());
        when(zoneRepository.existsById(zoneId)).thenReturn(true);
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));

        // Act
        ThresholdResponse response = thresholdService.getThresholdByZoneId(zoneId);

        // Assert
        assertNotNull(response);
        assertEquals(20.0, response.getMinTemperature());
    }

    @Test
    public void whenFarmerNotAssigned_andUpdatingThreshold_thenThrowForbidden() {
        // Arrange
        setupSecurityContext("farmerA", Role.FARMER, new ArrayList<>());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            thresholdService.updateThreshold(zoneId, updateRequest);
        });

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertTrue(exception.getMessage().contains("You are not assigned to this zone"));
        verify(thresholdRepository, never()).save(any());
    }

    @Test
    public void whenFarmerAssigned_andUpdatingThreshold_thenAllowAndSave() {
        // Arrange
        List<Zone> assignedZones = new ArrayList<>();
        assignedZones.add(zone);
        setupSecurityContext("farmerA", Role.FARMER, assignedZones);

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));
        when(thresholdRepository.save(any(Threshold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ThresholdResponse response = thresholdService.updateThreshold(zoneId, updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals(21.0, response.getMinTemperature());
        verify(thresholdRepository, times(1)).save(any(Threshold.class));
    }

    @Test
    public void whenOwner_andReadingThreshold_thenAllowAccess() {
        // Arrange
        setupSecurityContext("ownerA", Role.OWNER, new ArrayList<>());
        when(zoneRepository.existsById(zoneId)).thenReturn(true);
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));

        // Act
        ThresholdResponse response = thresholdService.getThresholdByZoneId(zoneId);

        // Assert
        assertNotNull(response);
        assertEquals(20.0, response.getMinTemperature());
    }

    @Test
    public void whenOwner_andUpdatingThreshold_thenThrowForbidden() {
        // Arrange
        setupSecurityContext("ownerA", Role.OWNER, new ArrayList<>());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            thresholdService.updateThreshold(zoneId, updateRequest);
        });

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertTrue(exception.getMessage().contains("Owners are not permitted to edit thresholds"));
        verify(thresholdRepository, never()).save(any());
    }

    @Test
    public void whenFarmerAssigned_andAttemptingToModifyLpgThreshold_thenThrowForbidden() {
        // Arrange
        List<Zone> assignedZones = new ArrayList<>();
        assignedZones.add(zone);
        setupSecurityContext("farmerA", Role.FARMER, assignedZones);

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));
        when(thresholdRepository.findByZoneId(zoneId)).thenReturn(Optional.of(threshold));

        // Create a copy of updateRequest with a different LPG threshold value
        ThresholdUpdateRequest modifiedRequest = new ThresholdUpdateRequest();
        modifiedRequest.setMinTemperature(21.0);
        modifiedRequest.setMaxTemperature(29.0);
        modifiedRequest.setMinHumidity(45.0);
        modifiedRequest.setMaxHumidity(75.0);
        modifiedRequest.setMaxNh3(18.0);
        modifiedRequest.setMaxLpg(1.5); // Modified value (different from threshold's 2.0)
        modifiedRequest.setAutoThresholdEnabled(false);
        modifiedRequest.setManualOverrideEnabled(false);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            thresholdService.updateThreshold(zoneId, modifiedRequest);
        });

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertTrue(exception.getMessage().contains("LPG threshold cannot be modified by farmers."));
        verify(thresholdRepository, never()).save(any());
    }
}
