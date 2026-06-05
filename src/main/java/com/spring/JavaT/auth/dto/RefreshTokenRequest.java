package com.spring.JavaT.auth.dto;

import com.spring.JavaT.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request body for refreshing access tokens")
public class RefreshTokenRequest {

    @Schema(description = "Valid refresh token issued at login or registration", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = ValidationMessages.REQUIRED)
    private String refreshToken;
}
