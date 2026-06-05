package com.spring.JavaT.notification.dto;

import com.spring.JavaT.common.NotificationType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NotificationResponse {
    Long id;
    Long customerId;
    Long billId;
    String message;
    NotificationType notificationType;
    boolean readFlag;
    boolean emailSent;
    Instant createdAt;
}
