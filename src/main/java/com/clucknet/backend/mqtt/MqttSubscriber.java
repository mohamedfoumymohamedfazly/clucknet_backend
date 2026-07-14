package com.clucknet.backend.mqtt;

import com.clucknet.backend.dto.model.TelemetryPayload;
import com.clucknet.backend.service.TelemetryProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import com.clucknet.backend.util.LatencyTracker;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Slf4j
public class MqttSubscriber implements MqttCallbackExtended, ApplicationListener<ApplicationReadyEvent> {

    private final MqttClient mqttClient;
    private final MqttConnectOptions connectOptions;
    private final TelemetryProcessingService telemetryProcessingService;
    private final ObjectMapper objectMapper;

    @Value("${aws.iot.mqtt.topics.telemetry}")
    private String telemetryTopic;

    @Value("${aws.iot.mqtt.topics.alerts}")
    private String alertsTopic;

    public MqttSubscriber(MqttClient mqttClient,
                          MqttConnectOptions connectOptions,
                          TelemetryProcessingService telemetryProcessingService,
                          ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.connectOptions = connectOptions;
        this.telemetryProcessingService = telemetryProcessingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        try {
            mqttClient.setCallback(this);
            log.info("Connecting to MQTT broker...");
            mqttClient.connect(connectOptions);
        } catch (MqttException ex) {
            log.error("Failed to establish initial connection to MQTT Broker: {}", ex.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT Connection established successfully. Server: {}, Reconnected: {}", serverURI, reconnect);
        try {
            // CRITICAL CHANGE: Convert Spring style {zoneId} or {deviceId} placeholders to MQTT '+' wildcards
            String mqttTelemetryTopic = telemetryTopic.replaceAll("\\{[^}]+\\}", "+");
            String mqttAlertsTopic = alertsTopic.replaceAll("\\{[^}]+\\}", "+");

            mqttClient.subscribe(new String[]{mqttTelemetryTopic, mqttAlertsTopic}, new int[]{1, 1});
            log.info("Successfully subscribed to MQTT topics: [{}] and [{}]", mqttTelemetryTopic, mqttAlertsTopic);
        } catch (MqttException ex) {
            log.error("Failed to subscribe to MQTT topics: {}", ex.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost! Cause: {}. Reconnecting in the background...",
                (cause != null ? cause.getMessage() : "Unknown"));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        long iotReceivedAt = System.currentTimeMillis();
        LatencyTracker.setIotReceivedAt(iotReceivedAt);
        log.info("[LATENCY_LOG] Stage 2: AWS IoT event received. Topic: {}, Timestamp: {}", topic, iotReceivedAt);

        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.debug("MQTT message received on topic: {}. Payload: {}", topic, payload);

        try {
            if (isTelemetryTopic(topic)) {
                TelemetryPayload telemetryPayload = objectMapper.readValue(payload, TelemetryPayload.class);
                // Optional: You can also log the Java object to see how Jackson parsed it
                log.info("PARSED OBJECT -> {}", telemetryPayload);
                telemetryProcessingService.processTelemetry(telemetryPayload);
            } else if (isAlertTopic(topic)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> alertData = objectMapper.readValue(payload, Map.class);

                // CRITICAL CHANGE: Adjusted extraction logic for the new topic hierarchy
                String zoneId = extractZoneIdFromTopic(topic);

                Double lpgValue = getDoubleValue(alertData.containsKey("triggerValue") ? alertData.get("triggerValue") : alertData.get("lpg"));
                Double thresholdValue = getDoubleValue(alertData.containsKey("thresholdLimit") ? alertData.get("thresholdLimit") : alertData.get("threshold"));

                log.info("PARSED OBJECT -> {}", alertData);

                // Assuming your service processes alerts by location/zoneId now
                telemetryProcessingService.processEdgeLpgAlert(zoneId, lpgValue, thresholdValue);
            }
        } catch (Exception ex) {
            log.error("Failed to parse and process inbound MQTT payload on topic {}: {}", topic, ex.getMessage(), ex);
        } finally {
            LatencyTracker.clear();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    // CRITICAL CHANGE: Regex updated to support the new '/zones/+/telemetry' structure
    private boolean isTelemetryTopic(String topic) {
        return topic.matches("^clucknet/zones/[^/]+/telemetry$");
    }

    // CRITICAL CHANGE: Regex updated assuming alerts also follow the pattern: 'clucknet/zones/{zoneId}/alerts'
    private boolean isAlertTopic(String topic) {
        return topic.matches("^clucknet/zones/[^/]+/alerts$");
    }

    // CRITICAL CHANGE: Updated array index parsing since the dynamic ID moved to index 2
    private String extractZoneIdFromTopic(String topic) {
        // String split of "clucknet/zones/zone-123/alerts" results in:
        // [0] "clucknet", [1] "zones", [2] "zone-123", [3] "alerts"
        String[] parts = topic.split("/");
        return parts.length > 2 ? parts[2] : "unknown-zone";
    }

    private Double getDoubleValue(Object val) {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return val != null ? Double.parseDouble(val.toString()) : 0.0;
    }
}
