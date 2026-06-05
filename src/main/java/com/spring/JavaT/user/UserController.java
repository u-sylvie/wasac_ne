package com.spring.JavaT.user;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.common.pagination.PageResponse;
import com.spring.JavaT.common.pagination.PaginationUtil;
import com.spring.JavaT.common.validation.ValidationGroups;
import com.spring.JavaT.user.dto.AdminCreateUserRequest;
import com.spring.JavaT.user.dto.UpdatePasswordRequest;
import com.spring.JavaT.user.dto.UpdateProfileRequest;
import com.spring.JavaT.user.dto.UpdateRoleRequest;
import com.spring.JavaT.user.dto.UserDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User management endpoints.
 *
 * <p>Two access tiers:
 * <ul>
 *   <li><b>Self-service</b> ({@code /me}) — any authenticated user can read and
 *       update their own profile.</li>
 *   <li><b>Admin</b> ({@code /{id}}) — only {@code ADMIN} role can list all users,
 *       view any user, change roles, and deactivate/activate accounts.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Profile, role, and account management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // =========================================================================
    // Self-service — /me
    // =========================================================================

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserDto>> getMyProfile(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {

        UserDto dto = userService.getMyProfile(principal.getUsername());
        return ResponseBuilder.ok(dto, "Profile retrieved successfully", request);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the authenticated user's profile (name, username)")
    public ResponseEntity<ApiResponse<UserDto>> updateMyProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Validated(ValidationGroups.OnPatch.class) @RequestBody UpdateProfileRequest body,
            HttpServletRequest request) {

        UserDto dto = userService.updateMyProfile(principal.getUsername(), body);
        return ResponseBuilder.ok(dto, "Profile updated successfully", request);
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Change the authenticated user's password")
    public ResponseEntity<ApiResponse<Void>> updateMyPassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdatePasswordRequest body,
            HttpServletRequest request) {

        userService.updateMyPassword(principal.getUsername(), body);
        return ResponseBuilder.ok("Password changed successfully", request);
    }

    // =========================================================================
    // Admin — /{id}
    // =========================================================================

    /** Allowed sort fields for the user list — prevents clients probing internal field names. */
    private static final Set<String> USER_SORT_FIELDS = Set.of(
            "id", "firstName", "lastName", "email", "username", "role", "status", "createdAt"
    );

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users with optional filtering — ADMIN only")
    public ResponseEntity<ApiResponse<PageResponse<UserDto>>> getAllUsers(
            // Pagination params
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Items per page (max 100)", example = "10")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction: asc or desc", example = "desc")
            @RequestParam(required = false) String sortDir,
            // Filter params
            @Parameter(description = "Filter by role: ADMIN, OPERATOR, FINANCE, CUSTOMER")
            @RequestParam(required = false) String role,
            @Parameter(description = "Filter by status: ACTIVE, INACTIVE, SUSPENDED, PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "Search by first name, last name, or email (partial match)")
            @RequestParam(required = false) String search,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortDir, USER_SORT_FIELDS);

        List<SearchCriteria> criteria = new ArrayList<>();
        if (role   != null && !role.isBlank())   criteria.add(new SearchCriteria("role",   SearchCriteria.Op.EQ,   role.toUpperCase()));
        if (status != null && !status.isBlank()) criteria.add(new SearchCriteria("status", SearchCriteria.Op.EQ,   status.toUpperCase()));
        if (search != null && !search.isBlank()) criteria.add(new SearchCriteria("email",  SearchCriteria.Op.LIKE, search));

        Page<UserDto> userPage = userService.getAllUsers(criteria, pageable);
        PageResponse<UserDto> response = PageResponse.of(userPage);
        return ResponseBuilder.ok(response, "Users retrieved successfully", request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an operator or finance user — ADMIN only")
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @Valid @RequestBody AdminCreateUserRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {

        UserDto dto = userService.createUserByAdmin(body, principal.getUsername());
        return ResponseBuilder.created(dto, "User created and credentials emailed successfully", request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get any user by ID — ADMIN only")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(
            @PathVariable Long id,
            HttpServletRequest request) {

        UserDto dto = userService.getUserById(id);
        return ResponseBuilder.ok(dto, "User retrieved successfully", request);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a user's role — ADMIN only")
    public ResponseEntity<ApiResponse<UserDto>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest body,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {

        UserDto dto = userService.updateRole(id, body, principal.getUsername());
        return ResponseBuilder.ok(dto, "Role updated successfully", request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-deactivate a user account — ADMIN only")
    public ResponseEntity<ApiResponse<UserDto>> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {

        UserDto dto = userService.deactivateUser(id, principal.getUsername());
        return ResponseBuilder.ok(dto, "User deactivated successfully", request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore a deactivated user account — ADMIN only")
    public ResponseEntity<ApiResponse<UserDto>> activateUser(
            @PathVariable Long id,
            HttpServletRequest request) {

        UserDto dto = userService.activateUser(id);
        return ResponseBuilder.ok(dto, "User activated successfully", request);
    }
}
