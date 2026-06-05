package com.spring.JavaT.auth.dto;

import com.spring.JavaT.common.OtpPurpose;
import com.spring.JavaT.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request body for sending or resending a one-time password")
public class ResendOtpRequest {

    @Schema(description = "Email address to send the OTP to", example = "john.doe@example.com")
    @NotBlank(message = ValidationMessages.EMAIL_REQUIRED)
    @Email(message = ValidationMessages.EMAIL_INVALID)
    private String email;

    @Schema(description = "Purpose of the OTP", example = "EMAIL_VERIFICATION")
    @NotNull(message = ValidationMessages.REQUIRED)
    private OtpPurpose purpose;
}
