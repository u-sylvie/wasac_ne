package com.spring.JavaT.customer;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.common.validation.ValidRwandaNationalId;
import com.spring.JavaT.customer.dto.CustomerCreateRequest;
import com.spring.JavaT.customer.dto.CustomerResponse;
import com.spring.JavaT.customer.dto.CustomerUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Customer management — National ID is the primary business identifier.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Validated
@Tag(name = "Customer Management", description = "Register and manage utility billing customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "fullName", "email", "nationalId", "status", "createdAt");
    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "List customers with optional filters — ADMIN or FINANCE only")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @Parameter(description = "Exact match on 16-digit National ID")
            @RequestParam(required = false) String nationalId,
            @Parameter(description = "Partial match on full name")
            @RequestParam(required = false) String search,
            HttpServletRequest request) {
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = new ArrayList<>();
        if (nationalId != null && !nationalId.isBlank()) {
            criteria.add(new SearchCriteria("nationalId", SearchCriteria.Op.EQ, nationalId.replaceAll("\\s+", "")));
        }
        if (search != null && !search.isBlank()) {
            criteria.add(new SearchCriteria("fullName", SearchCriteria.Op.LIKE, search));
        }
        Page<CustomerResponse> response = customerService.getAll(criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Customers retrieved successfully", request);
    }

    @GetMapping("/by-national-id/{nationalId}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','OPERATOR')")
    @Operation(summary = "Look up customer by Rwanda National ID — ADMIN, FINANCE, or OPERATOR")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByNationalId(
            @PathVariable @ValidRwandaNationalId String nationalId,
            HttpServletRequest request) {
        return ResponseBuilder.ok(
                customerService.getByNationalId(nationalId),
                "Customer retrieved successfully",
                request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Get customer by ID — ADMIN or FINANCE only")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable Long id, HttpServletRequest request) {
        return ResponseBuilder.ok(customerService.getById(id), "Customer retrieved successfully", request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new customer — ADMIN only")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CustomerCreateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.created(customerService.create(body, principal.getUsername()), "Customer created successfully", request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a customer — ADMIN only")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(customerService.update(id, body, principal.getUsername()), "Customer updated successfully", request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a customer — ADMIN only")
    public ResponseEntity<ApiResponse<CustomerResponse>> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(customerService.deactivate(id, principal.getUsername()), "Customer deactivated successfully", request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate a customer — ADMIN only")
    public ResponseEntity<ApiResponse<CustomerResponse>> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(customerService.activate(id, principal.getUsername()), "Customer activated successfully", request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a customer — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        customerService.delete(id, principal.getUsername());
        return ResponseBuilder.ok("Customer deleted successfully", request);
    }
}
