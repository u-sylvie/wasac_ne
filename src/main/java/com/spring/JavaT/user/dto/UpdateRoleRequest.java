package com.spring.JavaT.user.dto;

import com.spring.JavaT.common.validation.ValidEnum;
import com.spring.JavaT.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for changing a user's role (PATCH /users/{id}/role).
 * Admin-only operation.
 */
@Getter
@Setter
@Schema(description = "Request body for updating a user's role")
public class UpdateRoleRequest {

    @Schema(description = "New role to assign", example = "OPERATOR",
            allowableValues = {"ADMIN", "OPERATOR", "FINANCE", "CUSTOMER"})
    @NotNull(message = "Role is required")
    @ValidEnum(enumClass = Role.class, message = "Invalid role. Accepted values: ADMIN, OPERATOR, FINANCE, CUSTOMER")
    private String role;
}
