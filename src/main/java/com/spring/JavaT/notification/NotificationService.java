package com.spring.JavaT.notification;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.NotificationType;
import com.spring.JavaT.common.SecurityHelper;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.exception.ResourceNotFoundException;
import com.spring.JavaT.notification.dto.NotificationResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final SecurityHelper securityHelper;

    public Page<NotificationResponse> getAll(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<Notification> spec = new BaseSpecification<>(criteria);
        return notificationRepository.findAll(spec, pageable).map(NotificationMapper::toResponse);
    }

    public Page<NotificationResponse> getAllForCustomer(Long customerId, List<SearchCriteria> criteria, Pageable pageable) {
        List<SearchCriteria> allCriteria = new java.util.ArrayList<>(criteria);
        allCriteria.add(new SearchCriteria("customer.id", SearchCriteria.Op.EQ, customerId));
        return getAll(allCriteria, pageable);
    }

    public NotificationResponse getById(Long id) {
        return NotificationMapper.toResponse(findOrThrow(id));
    }

    public NotificationResponse getByIdForCustomer(Long id, String userEmail) {
        Notification notification = findOrThrow(id);
        securityHelper.ensureCustomerOwns(notification.getCustomer().getId(), userEmail);
        return NotificationMapper.toResponse(notification);
    }

    @Transactional
    public NotificationResponse markRead(Long id, String actor) {
        Notification notification = findOrThrow(id);
        notification.setReadFlag(true);
        Notification saved = notificationRepository.save(notification);
        auditService.log("Notification", saved.getId(), "UPDATE", actor, "Marked notification as read");
        return NotificationMapper.toResponse(saved);
    }

    @Transactional
    public NotificationResponse markReadForCustomer(Long id, String userEmail) {
        Notification notification = findOrThrow(id);
        securityHelper.ensureCustomerOwns(notification.getCustomer().getId(), userEmail);
        return markRead(id, userEmail);
    }

    @Transactional
    public int sendPendingEmails(String actor) {
        List<Notification> pending = notificationRepository.findByEmailSentFalseOrderByCreatedAtAsc();
        int sent = 0;

        for (Notification notification : pending) {
            String email = notification.getCustomer().getEmail();
            String name = notification.getCustomer().getFullName();

            if (notification.getNotificationType() == NotificationType.BILL_GENERATED
                    && notification.getBill() != null) {
                emailService.sendBillNotificationEmail(
                        email, name,
                        notification.getBill().getReference(),
                        notification.getBill().getTotalAmount().toPlainString(),
                        notification.getBill().getBillingMonth(),
                        notification.getBill().getBillingYear());
            } else if (notification.getNotificationType() == NotificationType.PAYMENT_COMPLETED
                    && notification.getBill() != null) {
                emailService.sendPaymentNotificationEmail(
                        email, name,
                        notification.getBill().getReference(),
                        notification.getBill().getTotalAmount().toPlainString());
            }

            notification.setEmailSent(true);
            notificationRepository.save(notification);
            sent++;
        }

        if (sent > 0) {
            auditService.log("Notification", 0L, "UPDATE", actor, "Sent " + sent + " pending notification emails");
            log.info("Sent {} pending notification emails", sent);
        }
        return sent;
    }

    private Notification findOrThrow(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
    }
}
