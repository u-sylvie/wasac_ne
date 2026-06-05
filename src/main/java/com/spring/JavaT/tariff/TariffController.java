package com.spring.JavaT.tariff;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.tariff.dto.TariffCreateRequest;
import com.spring.JavaT.tariff.dto.TariffResponse;
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
 * Versioned water and electricity tariff configuration.
 */
@RestController
@RequestMapping("/api/v1/tariffs")
@RequiredArgsConstructor
@Tag(name = "Tariff Management", description = "Configure versioned water and electricity pricing")
@SecurityRequirement(name = "bearerAuth")
public class TariffController {

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "name", "meterType", "version", "effectiveFrom", "createdAt"
    );

    private final TariffService tariffService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List tariff versions — ADMIN only")
    public ResponseEntity<ApiResponse<PageResponse<TariffResponse>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        List<SearchCriteria> criteria = new ArrayList<>();
        if (meterType != null && !meterType.isBlank()) {
            criteria.add(new SearchCriteria("meterType", SearchCriteria.Op.EQ, meterType.toUpperCase()));
        }
        if (active != null) criteria.add(new SearchCriteria("active", SearchCriteria.Op.EQ, active));
        if (search != null && !search.isBlank()) {
            criteria.add(new SearchCriteria("name", SearchCriteria.Op.LIKE, search));
        }

        Page<TariffResponse> response = tariffService.getAll(criteria, pageable);
        return ResponseBuilder.ok(PageResponse.of(response), "Tariffs retrieved successfully", request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get tariff by ID — ADMIN only")
    public ResponseEntity<ApiResponse<TariffResponse>> getById(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ResponseBuilder.ok(tariffService.getById(id), "Tariff retrieved successfully", request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new tariff version — ADMIN only")
    public ResponseEntity<ApiResponse<TariffResponse>> create(
            @Valid @RequestBody TariffCreateRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.created(
                tariffService.create(body, principal.getUsername()),
                "Tariff created successfully",
                request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a tariff version — ADMIN only")
    public ResponseEntity<ApiResponse<TariffResponse>> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        return ResponseBuilder.ok(
                tariffService.deactivate(id, principal.getUsername()),
                "Tariff deactivated successfully",
                request);
    }
}
