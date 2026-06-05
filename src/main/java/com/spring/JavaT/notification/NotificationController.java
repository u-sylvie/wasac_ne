package com.spring.JavaT.notification;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.SecurityHelper;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.customer.Customer;
import com.spring.JavaT.notification.dto.NotificationResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "notificationType", "readFlag", "emailSent", "createdAt"
    );

    private final NotificationService notificationService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Boolean readFlag,
            @RequestParam(required = false) String notificationType,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = buildCriteria(customerId, readFlag, notificationType);
        Page<NotificationResponse> response = notificationService.getAll(criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Notifications retrieved successfully", request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> myNotifications(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Boolean readFlag,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {

        Customer customer = securityHelper.requireCustomerForUser(principal.getUsername());
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = buildCriteria(null, readFlag, null);
        Page<NotificationResponse> response = notificationService.getAllForCustomer(customer.getId(), criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Your notifications retrieved successfully", request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ResponseBuilder.ok(notificationService.getById(id), "Notification retrieved successfully", request);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(
                notificationService.markRead(id, principal.getUsername()),
                "Notification marked as read",
                request);
    }

    @PatchMapping("/me/{id}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<NotificationResponse>> markMyNotificationRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(
                notificationService.markReadForCustomer(id, principal.getUsername()),
                "Notification marked as read",
                request);
    }

    @PostMapping("/send-pending-emails")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> sendPendingEmails(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        int sent = notificationService.sendPendingEmails(principal.getUsername());
        return ResponseBuilder.ok(Map.of("sent", sent), sent + " notification email(s) sent", request);
    }

    private List<SearchCriteria> buildCriteria(Long customerId, Boolean readFlag, String notificationType) {
        List<SearchCriteria> criteria = new ArrayList<>();
        if (customerId != null) criteria.add(new SearchCriteria("customer.id", SearchCriteria.Op.EQ, customerId));
        if (readFlag != null) criteria.add(new SearchCriteria("readFlag", SearchCriteria.Op.EQ, readFlag));
        if (notificationType != null && !notificationType.isBlank()) {
            criteria.add(new SearchCriteria("notificationType", SearchCriteria.Op.EQ, notificationType.toUpperCase()));
        }
        return criteria;
    }
}
