package com.clucknet.backend.controller;

import com.clucknet.backend.dto.request.FcmTokenRequest;
import com.clucknet.backend.dto.response.ApiResponse;
import com.clucknet.backend.dto.response.NotificationResponse;
import com.clucknet.backend.entity.Notification;
import com.clucknet.backend.exception.ResourceNotFoundException;
import com.clucknet.backend.repository.NotificationRepository;
import com.clucknet.backend.security.CustomUserDetails;
import com.clucknet.backend.security.role.AuthorityConstants;
import com.clucknet.backend.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationService notificationService,
                                  NotificationRepository notificationRepository) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        List<Notification> notifications = notificationService.getNotificationsForUser(
                userDetails.getId(),
                PageRequest.of(0, 1000)
        ).getContent();

        List<NotificationResponse> responseList = notifications.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully.", responseList));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotificationById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));

        if (!notification.getUser().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied."));
        }

        return ResponseEntity.ok(ApiResponse.success("Notification retrieved successfully.", mapToResponse(notification)));
    }

    @PostMapping("/mark-all-read")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        notificationService.markAllAsRead(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read."));
    }

    @RequestMapping(value = "/{id}/read", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));

        if (!notification.getUser().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied."));
        }

        notification.setIsRead(true);
        Notification updated = notificationRepository.save(notification);

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read.", mapToResponse(updated)));
    }

    @PostMapping("/fcm-token")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<Void>> registerFcmToken(
            @RequestBody @Valid FcmTokenRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.registerFcmToken(userDetails.getId(), request.getToken());
        return ResponseEntity.ok(ApiResponse.success("FCM token registered successfully."));
    }

    @PostMapping("/fcm-token/unregister")
    @PreAuthorize(AuthorityConstants.HAS_FARMER_OR_OWNER)
    public ResponseEntity<ApiResponse<Void>> unregisterFcmToken(
            @RequestBody @Valid FcmTokenRequest request) {
        notificationService.unregisterFcmToken(request.getToken());
        return ResponseEntity.ok(ApiResponse.success("FCM token unregistered successfully."));
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .timestamp(notification.getCreatedAt())
                .build();
    }
}
