package com.spring.JavaT.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Response body returned after a successful login or registration.
 *
 * <p>The {@code refreshToken} is included only when the server issues one
 * (omitted from JSON when null via {@link JsonInclude}).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authentication tokens returned after successful login or registration")
public class AuthResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private final String accessToken;

    @Schema(description = "JWT refresh token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private final String refreshToken;

    @Schema(description = "Token type, always Bearer", example = "Bearer")
    @Builder.Default
    private final String tokenType = "Bearer";

    @Schema(description = "Access token validity in seconds", example = "86400")
    private final long expiresIn;

    @Schema(description = "Authenticated user's email", example = "john.doe@example.com")
    private final String email;

    @Schema(description = "Authenticated user's role", example = "USER")
    private final String role;

    @Schema(description = "True when the user must change a temporary password before using the API")
    private final Boolean mustChangePassword;

    @Schema(description = "Billing customer ID — returned on self-registration only", example = "2")
    private final Long customerId;
}
