package com.clucknet.backend.service;

import com.clucknet.backend.entity.Threshold;
import com.clucknet.backend.service.impl.MqttSyncPublisherImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MqttSyncPublisherTest {

    @Mock
    private MqttClient mqttClient;

    private MqttSyncPublisherImpl mqttSyncPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        mqttSyncPublisher = new MqttSyncPublisherImpl(mqttClient, objectMapper);
    }

    @Test
    public void whenThresholdUpdated_thenPublishFormattedPayloadToMqtt() throws Exception {
        // Arrange
        Long zoneId = 1L;
        Threshold threshold = Threshold.builder()
                .minTemperature(22.0)
                .maxTemperature(34.0)
                .minHumidity(40.0)
                .maxHumidity(80.0)
                .maxNh3(25.0)
                .maxLpg(250.0)
                .build();

        when(mqttClient.isConnected()).thenReturn(true);

        // Act
        mqttSyncPublisher.publishThresholdUpdate(zoneId, threshold);

        // Assert
        String expectedTopic = "clucknet/zones/1/thresholds";
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);

        verify(mqttClient, times(1)).publish(eq(expectedTopic), messageCaptor.capture());

        MqttMessage publishedMessage = messageCaptor.getValue();
        assertNotNull(publishedMessage);
        assertEquals(1, publishedMessage.getQos()); // Must be QOS 1
        assertTrue(publishedMessage.isRetained());   // Must be retained

        String publishedPayload = new String(publishedMessage.getPayload(), StandardCharsets.UTF_8);
        assertTrue(publishedPayload.contains("\"minTemperature\":22.0"));
        assertTrue(publishedPayload.contains("\"maxTemperature\":34.0"));
        assertTrue(publishedPayload.contains("\"maxNh3\":25.0"));
    }

    @Test
    public void whenClientDisconnected_thenSkipPublishingAndDoNotThrowException() throws Exception {
        // Arrange
        Long zoneId = 1L;
        Threshold threshold = Threshold.builder().build();
        when(mqttClient.isConnected()).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> mqttSyncPublisher.publishThresholdUpdate(zoneId, threshold));
        verify(mqttClient, never()).publish(anyString(), any(MqttMessage.class));
    }
}
