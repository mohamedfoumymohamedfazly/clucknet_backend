package com.clucknet.backend.service.impl;

import com.clucknet.backend.entity.Threshold;
import com.clucknet.backend.service.MqttSyncPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class MqttSyncPublisherImpl implements MqttSyncPublisher {

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    public MqttSyncPublisherImpl(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishThresholdUpdate(Long zoneId, Threshold threshold) {
        // Construct target synchronization topic, e.g., clucknet/zones/1/thresholds
        String topic = String.format("clucknet/zones/%d/thresholds", zoneId);

        try {
            // Check connection status before attempting to publish
            if (!mqttClient.isConnected()) {
                log.warn("Threshold Sync: MQTT client is currently offline. Skipping real-time edge sync for zone ID {}.", zoneId);
                return;
            }

            // Map updated settings into a clean, lightweight JSON carrier payload
            Map<String, Object> syncPayload = new HashMap<>();
            syncPayload.put("minTemperature", threshold.getEffectiveMinTemperature());
            syncPayload.put("maxTemperature", threshold.getEffectiveMaxTemperature());
            syncPayload.put("minHumidity", threshold.getEffectiveMinHumidity());
            syncPayload.put("maxHumidity", threshold.getEffectiveMaxHumidity());
            syncPayload.put("maxNh3", threshold.getMaxNh3());
            syncPayload.put("maxLpg", threshold.getMaxLpg());

            String jsonPayload = objectMapper.writeValueAsString(syncPayload);
            
            // Build the message. QOS 1 guarantees delivery to the edge nodes
            MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1); 
            message.setRetained(true); // Retain settings so newly powered-on edge nodes get parameters immediately!

            mqttClient.publish(topic, message);
            log.info("Threshold Sync Success: Dispatched updated parameters to topic '{}'", topic);
        } catch (Exception ex) {
            log.error("Threshold Sync Failure: Failed to sync thresholds to edge nodes on topic '{}': {}", topic, ex.getMessage(), ex);
            // Re-throw exception so that the caller service is aware, allowing resilient fallback logs
            throw new RuntimeException("MQTT threshold sync failure.", ex);
        }
    }
}
