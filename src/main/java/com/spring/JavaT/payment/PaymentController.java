package com.spring.JavaT.payment;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.SecurityHelper;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.customer.Customer;
import com.spring.JavaT.payment.dto.PaymentCreateRequest;
import com.spring.JavaT.payment.dto.PaymentResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import java.util.Set;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "amountPaid", "paymentDate", "paymentMethod", "createdAt"
    );

    private final PaymentService paymentService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Long billId,
            @RequestParam(required = false) String paymentMethod,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = buildCriteria(billId, paymentMethod);
        Page<PaymentResponse> response = paymentService.getAll(criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Payments retrieved successfully", request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> myPayments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {

        Customer customer = securityHelper.requireCustomerForUser(principal.getUsername());
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        Page<PaymentResponse> response = paymentService.getAllForCustomer(customer.getId(), List.of(), pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Your payments retrieved successfully", request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ResponseBuilder.ok(paymentService.getById(id), "Payment retrieved successfully", request);
    }

    @GetMapping("/me/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getMyPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(
                paymentService.getByIdForCustomer(id, principal.getUsername()),
                "Payment retrieved successfully",
                request);
    }

    @PostMapping
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> record(
            @Valid @RequestBody PaymentCreateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.created(
                paymentService.recordPayment(body, principal.getUsername()),
                "Payment recorded successfully",
                request);
    }

    private List<SearchCriteria> buildCriteria(Long billId, String paymentMethod) {
        List<SearchCriteria> criteria = new ArrayList<>();
        if (billId != null) criteria.add(new SearchCriteria("bill.id", SearchCriteria.Op.EQ, billId));
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            criteria.add(new SearchCriteria("paymentMethod", SearchCriteria.Op.EQ, paymentMethod.toUpperCase()));
        }
        return criteria;
    }
}
