package com.spring.JavaT.audit;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "View system audit trail — ADMIN only")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "entityName", "entityId", "action", "createdAt");

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all audit logs (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> listAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        Page<AuditLog> logs = auditService.findAll(pageable);
        return ResponseBuilder.ok(PageResponse.of(logs), "Audit logs retrieved successfully", request);
    }

    @GetMapping("/{entityName}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "List audit logs for a specific entity")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> listForEntity(
            @PathVariable String entityName,
            @PathVariable Long entityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, SORT_FIELDS);
        Page<AuditLog> logs = auditService.findByEntity(entityName, entityId, pageable);
        return ResponseBuilder.ok(PageResponse.of(logs), "Entity audit logs retrieved successfully", request);
    }
}
