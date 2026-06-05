package com.spring.JavaT.meterreading;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.meterreading.dto.MeterReadingCreateRequest;
import com.spring.JavaT.meterreading.dto.MeterReadingResponse;
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
 * Field meter readings captured by operators — basis for monthly billing.
 */
@RestController
@RequestMapping("/api/v1/meter-readings")
@RequiredArgsConstructor
@Tag(name = "Meter Readings", description = "Record monthly water/electricity consumption readings")
@SecurityRequirement(name = "bearerAuth")
public class MeterReadingController {

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "readingDate", "billingYear", "billingMonth", "createdAt"
    );

    private final MeterReadingService meterReadingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE')")
    @Operation(summary = "List meter readings — ADMIN, OPERATOR, or FINANCE only")
    public ResponseEntity<ApiResponse<PageResponse<MeterReadingResponse>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Long meterId,
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) Integer billingMonth,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = new ArrayList<>();
        if (meterId != null) criteria.add(new SearchCriteria("meter.id", SearchCriteria.Op.EQ, meterId));
        if (billingYear != null) criteria.add(new SearchCriteria("billingYear", SearchCriteria.Op.EQ, billingYear));
        if (billingMonth != null) criteria.add(new SearchCriteria("billingMonth", SearchCriteria.Op.EQ, billingMonth));

        Page<MeterReadingResponse> response = meterReadingService.getAll(criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Meter readings retrieved successfully", request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE')")
    @Operation(summary = "Get meter reading by ID — ADMIN, OPERATOR, or FINANCE only")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> getById(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ResponseBuilder.ok(meterReadingService.getById(id), "Meter reading retrieved successfully", request);
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Record a new meter reading — OPERATOR only")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> create(
            @Valid @RequestBody MeterReadingCreateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.created(
                meterReadingService.create(body, principal.getUsername()),
                "Meter reading created successfully",
                request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a meter reading — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        meterReadingService.delete(id, principal.getUsername());
        return ResponseBuilder.ok("Meter reading deleted successfully", request);
    }
}
