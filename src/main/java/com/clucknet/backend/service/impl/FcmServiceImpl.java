package com.clucknet.backend.service.impl;

import com.clucknet.backend.entity.DeviceToken;
import com.clucknet.backend.repository.DeviceTokenRepository;
import com.clucknet.backend.service.FcmService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FcmServiceImpl implements FcmService {

    private final DeviceTokenRepository deviceTokenRepository;

    public FcmServiceImpl(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Override
    @Async
    public void sendPushNotification(Long userId, String title, String message, String alertId, String zoneId, Long iotReceivedAt, Long alertGeneratedAt) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FCM Service: Firebase is not initialized. Skipping push notification.");
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("FCM Service: No registered devices for user ID {}", userId);
            return;
        }

        List<String> registrationTokens = tokens.stream()
                .map(DeviceToken::getToken)
                .collect(Collectors.toList());

        long sendInitiated = System.currentTimeMillis();
        log.info("[LATENCY_LOG] Stage 4: Firebase send request initiated. Alert ID: {}, User ID: {}, Timestamp: {}", 
                alertId, userId, sendInitiated);

        MulticastMessage multicastMessage = MulticastMessage.builder()
                .addAllTokens(registrationTokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(message)
                        .build())
                .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                .putData("alertId", alertId != null ? alertId : "")
                .putData("zoneId", zoneId != null ? zoneId : "")
                .putData("iotReceivedAt", iotReceivedAt != null ? String.valueOf(iotReceivedAt) : "")
                .putData("alertGeneratedAt", alertGeneratedAt != null ? String.valueOf(alertGeneratedAt) : "")
                .putData("fcmSentAt", String.valueOf(sendInitiated))
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .setChannelId("clucknet_alerts")
                                .setPriority(AndroidNotification.Priority.HIGH)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .putHeader("apns-push-type", "alert")
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(multicastMessage);
            long responseReceived = System.currentTimeMillis();
            log.info("[LATENCY_LOG] Stage 5: Firebase response received. Alert ID: {}, User ID: {}, Success: {}, Failure: {}, Timestamp: {}", 
                    alertId, userId, response.getSuccessCount(), response.getFailureCount(), responseReceived);
            
            // Clean up invalid/expired tokens
            if (response.getFailureCount() > 0) {
                for (int i = 0; i < response.getResponses().size(); i++) {
                    SendResponse sendResponse = response.getResponses().get(i);
                    if (!sendResponse.isSuccessful()) {
                        String badToken = registrationTokens.get(i);
                        log.info("FCM Service: Removing invalid token: {}", badToken);
                        deviceTokenRepository.deleteByToken(badToken);
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM Service: Error sending multicast message: {}", e.getMessage());
        }
    }
}
