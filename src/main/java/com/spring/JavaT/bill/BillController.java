package com.spring.JavaT.bill;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.SecurityHelper;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.bill.dto.BillGenerateRequest;
import com.spring.JavaT.bill.dto.BillResponse;
import com.spring.JavaT.customer.Customer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * Bill generation, approval, and customer self-service bill viewing.
 */
@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
@Tag(name = "Bill Management", description = "Generate, approve, and view utility bills")
@SecurityRequirement(name = "bearerAuth")
public class BillController {

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "reference", "billingYear", "billingMonth", "totalAmount", "billStatus", "createdAt"
    );

    private final BillService billService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "List all bills — ADMIN or FINANCE only")
    public ResponseEntity<ApiResponse<PageResponse<BillResponse>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String billStatus,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = buildCriteria(customerId, billStatus, search);
        Page<BillResponse> response = billService.getAll(criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Bills retrieved successfully", request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List own bills — CUSTOMER only")
    public ResponseEntity<ApiResponse<PageResponse<BillResponse>>> myBills(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String billStatus,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {

        Customer customer = securityHelper.requireCustomerForUser(principal.getUsername());
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = buildCriteria(null, billStatus, null);
        Page<BillResponse> response = billService.getAllForCustomer(customer.getId(), criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Your bills retrieved successfully", request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Get bill by ID — ADMIN or FINANCE only")
    public ResponseEntity<ApiResponse<BillResponse>> getById(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ResponseBuilder.ok(billService.getById(id), "Bill retrieved successfully", request);
    }

    @GetMapping("/me/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get own bill by ID — CUSTOMER only")
    public ResponseEntity<ApiResponse<BillResponse>> getMyBill(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(
                billService.getByIdForCustomer(id, principal.getUsername()),
                "Bill retrieved successfully",
                request);
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Generate a bill from a meter reading — ADMIN or FINANCE only")
    public ResponseEntity<ApiResponse<BillResponse>> generate(
            @Valid @RequestBody BillGenerateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.created(
                billService.generate(body, principal.getUsername()),
                "Bill generated successfully",
                request);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Approve a bill for payment — FINANCE only")
    public ResponseEntity<ApiResponse<BillResponse>> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(
                billService.approve(id, principal.getUsername()),
                "Bill approved successfully",
                request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a bill — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        billService.delete(id, principal.getUsername());
        return ResponseBuilder.ok("Bill deleted successfully", request);
    }

    private List<SearchCriteria> buildCriteria(Long customerId, String billStatus, String search) {
        List<SearchCriteria> criteria = new ArrayList<>();
        if (customerId != null) criteria.add(new SearchCriteria("customer.id", SearchCriteria.Op.EQ, customerId));
        if (billStatus != null && !billStatus.isBlank()) {
            criteria.add(new SearchCriteria("billStatus", SearchCriteria.Op.EQ, billStatus.toUpperCase()));
        }
        if (search != null && !search.isBlank()) {
            criteria.add(new SearchCriteria("reference", SearchCriteria.Op.LIKE, search));
        }
        return criteria;
    }
}
