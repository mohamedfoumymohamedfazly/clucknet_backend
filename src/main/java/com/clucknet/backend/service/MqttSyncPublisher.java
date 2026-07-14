package com.clucknet.backend.service;

import com.clucknet.backend.entity.Threshold;

public interface MqttSyncPublisher {
    
    // Publishes updated threshold parameters to AWS IoT Core to sync with Edge nodes
    void publishThresholdUpdate(Long zoneId, Threshold threshold);
}
