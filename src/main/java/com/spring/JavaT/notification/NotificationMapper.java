package com.spring.JavaT.notification;

import com.spring.JavaT.notification.dto.NotificationResponse;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .customerId(notification.getCustomer().getId())
                .billId(notification.getBill() != null ? notification.getBill().getId() : null)
                .message(notification.getMessage())
                .notificationType(notification.getNotificationType())
                .readFlag(notification.isReadFlag())
                .emailSent(notification.isEmailSent())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
