package com.spring.JavaT.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispatches bill/payment notification emails queued by PostgreSQL routines.
 *
 * <p>DB triggers and {@code sp_record_payment} insert rows with {@code email_sent = false}.
 * This scheduler picks them up and sends HTML emails via {@link EmailService}.
 * Finance/Admin can also trigger dispatch immediately via
 * {@code POST /api/v1/notifications/send-pending-emails}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmailScheduler {

    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.notifications.email-dispatch-interval-ms:120000}")
    public void dispatchPendingEmails() {
        int sent = notificationService.sendPendingEmails("system-scheduler");
        if (sent > 0) {
            log.info("Scheduler dispatched {} notification email(s)", sent);
        }
    }
}
