package com.clucknet.backend.service;

public interface FcmService {
    void sendPushNotification(Long userId, String title, String message, String alertId, String zoneId, Long iotReceivedAt, Long alertGeneratedAt);
}
