package com.clucknet.backend.service;

import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {

    // Dispatch a warning notice to all registered users (OWNER and FARMER) linked to this warning
    void sendNotificationForAlert(Alert alert);

    Page<Notification> getNotificationsForUser(Long userId, Pageable pageable);

    int markAllAsRead(Long userId);

    void registerFcmToken(Long userId, String token);

    void unregisterFcmToken(String token);
}
