package com.clucknet.backend.service.impl;

import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.DeviceToken;
import com.clucknet.backend.entity.Notification;
import com.clucknet.backend.entity.User;
import com.clucknet.backend.repository.DeviceTokenRepository;
import com.clucknet.backend.repository.NotificationRepository;
import com.clucknet.backend.repository.UserRepository;
import com.clucknet.backend.service.FcmService;
import com.clucknet.backend.service.NotificationService;
import com.clucknet.backend.util.LatencyTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmService fcmService;

    public NotificationServiceImpl(NotificationRepository notificationRepository, 
                                   UserRepository userRepository,
                                   DeviceTokenRepository deviceTokenRepository,
                                   FcmService fcmService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.fcmService = fcmService;
    }

    @Override
    @Transactional
    public void sendNotificationForAlert(Alert alert) {
        Long iotReceivedAt = LatencyTracker.getIotReceivedAt();
        Long alertGeneratedAt = LatencyTracker.getAlertGeneratedAt();
        long notificationCreatedTime = System.currentTimeMillis();
        log.info("[LATENCY_LOG] Stage 3: Notification creation started. Alert ID: {}, Timestamp: {}", 
                alert.getId(), notificationCreatedTime);

        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            log.warn("Notifications Dispatch: Skipping notice dispatch as no users are registered in the system.");
            return;
        }

        // Construct a clean, user-friendly descriptive warning message
        String warningMessage = buildAlertMessage(alert);
        String title = buildAlertTitle(alert);
        List<Notification> notificationList = new ArrayList<>();

        for (User user : users) {
            Notification notification = Notification.builder()
                    .title(title)
                    .message(warningMessage)
                    .isRead(false)
                    .user(user)
                    .alert(alert)
                    .build();
            notificationList.add(notification);

            try {
                fcmService.sendPushNotification(
                        user.getId(),
                        title,
                        warningMessage,
                        alert.getId() != null ? alert.getId().toString() : null,
                        alert.getZone() != null ? alert.getZone().getId().toString() : null,
                        iotReceivedAt,
                        alertGeneratedAt
                );
            } catch (Exception e) {
                log.error("Failed to send push notification to user ID: {}", user.getId(), e);
            }
        }

        // Persist notifications for all registered users in a single optimized DB batch operation
        notificationRepository.saveAll(notificationList);
        log.info("Warning notice successfully logged for all {} registered farm users.", users.size());
    }

    private String buildAlertTitle(Alert alert) {
        if (alert.getType() == null) {
            return "Alert Notification";
        }
        switch (alert.getType()) {
            case TEMP_HIGH:
                return "High temperature";
            case TEMP_LOW:
                return "Low temperature";
            case HUMIDITY_HIGH:
                return "High humidity";
            case HUMIDITY_LOW:
                return "Low humidity";
            case NH3_HIGH:
                return "High ammonia";
            case LPG_DANGER:
                return "Gas detected";
            default:
                return "Alert Notification";
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getNotificationsForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        // Run our optimized single-statement SQL UPDATE bulk query
        int updatedCount = notificationRepository.markAllAsReadForUser(userId);
        log.info("Marked {} unread notifications as read for user ID: {}", updatedCount, userId);
        return updatedCount;
    }

    // Helper to synthesize standard warning texts
    private String buildAlertMessage(Alert alert) {
        String zoneName = alert.getZone().getName();
        switch (alert.getType()) {
            case TEMP_HIGH:
                return String.format("🌡️ High temperature in %s", zoneName);
            case TEMP_LOW:
                return String.format("🌡️ Low temperature in %s", zoneName);
            case HUMIDITY_HIGH:
                return String.format("💧 High humidity in %s", zoneName);
            case HUMIDITY_LOW:
                return String.format("💧 Low humidity in %s", zoneName);
            case NH3_HIGH:
                return String.format("⚠️ High ammonia in %s", zoneName);
            case LPG_DANGER:
                return String.format("⚠️ Gas detected in %s", zoneName);
            default:
                return String.format("⚠️ Climate alert in %s", zoneName);
        }
    }

    @Override
    @Transactional
    public void registerFcmToken(Long userId, String token) {
        Optional<DeviceToken> existingToken = deviceTokenRepository.findByToken(token);
        if (existingToken.isPresent()) {
            DeviceToken deviceToken = existingToken.get();
            if (!deviceToken.getUser().getId().equals(userId)) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
                deviceToken.setUser(user);
                deviceTokenRepository.save(deviceToken);
                log.info("FCM Token reassigned to user ID: {}", userId);
            } else {
                // Token already registered for this user, just update the timestamp
                deviceTokenRepository.save(deviceToken);
            }
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
            DeviceToken deviceToken = DeviceToken.builder()
                    .user(user)
                    .token(token)
                    .build();
            deviceTokenRepository.save(deviceToken);
            log.info("Registered new FCM Token for user ID: {}", userId);
        }
    }

    @Override
    @Transactional
    public void unregisterFcmToken(String token) {
        deviceTokenRepository.deleteByToken(token);
        log.info("Unregistered FCM Token.");
    }
}
