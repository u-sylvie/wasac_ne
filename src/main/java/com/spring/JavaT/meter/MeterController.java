package com.spring.JavaT.meter;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.meter.dto.MeterCreateRequest;
import com.spring.JavaT.meter.dto.MeterResponse;
import com.spring.JavaT.meter.dto.MeterUpdateRequest;
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
 * Water and electricity meter registration and assignment to customers.
 */
@RestController
@RequestMapping("/api/v1/meters")
@RequiredArgsConstructor
@Tag(name = "Meter Management", description = "Assign and manage customer utility meters")
@SecurityRequirement(name = "bearerAuth")
public class MeterController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "meterNumber", "meterType", "status", "createdAt");
    private final MeterService meterService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE')")
    @Operation(summary = "List meters — ADMIN, OPERATOR, or FINANCE only")
    public ResponseEntity<ApiResponse<PageResponse<MeterResponse>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = new ArrayList<>();
        if (customerId != null) criteria.add(new SearchCriteria("customer.id", SearchCriteria.Op.EQ, customerId));
        if (search != null && !search.isBlank()) criteria.add(new SearchCriteria("meterNumber", SearchCriteria.Op.LIKE, search));
        Page<MeterResponse> response = meterService.getAll(criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Meters retrieved successfully", request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE')")
    @Operation(summary = "Get meter by ID — ADMIN, OPERATOR, or FINANCE only")
    public ResponseEntity<ApiResponse<MeterResponse>> getById(@PathVariable Long id, HttpServletRequest request) {
        return ResponseBuilder.ok(meterService.getById(id), "Meter retrieved successfully", request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a new meter to a customer — ADMIN only")
    public ResponseEntity<ApiResponse<MeterResponse>> create(
            @Valid @RequestBody MeterCreateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.created(meterService.create(body, principal.getUsername()), "Meter created successfully", request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a meter — ADMIN only")
    public ResponseEntity<ApiResponse<MeterResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody MeterUpdateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(meterService.update(id, body, principal.getUsername()), "Meter updated successfully", request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a meter — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        meterService.delete(id, principal.getUsername());
        return ResponseBuilder.ok("Meter deleted successfully", request);
    }
}
