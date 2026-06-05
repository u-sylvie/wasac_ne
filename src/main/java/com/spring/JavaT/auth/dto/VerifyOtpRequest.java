package com.spring.JavaT.auth.dto;

import com.spring.JavaT.common.OtpPurpose;
import com.spring.JavaT.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request body for verifying a one-time password")
public class VerifyOtpRequest {

    @Schema(description = "Email address the OTP was sent to", example = "john.doe@example.com")
    @NotBlank(message = ValidationMessages.EMAIL_REQUIRED)
    @Email(message = ValidationMessages.EMAIL_INVALID)
    private String email;

    @Schema(description = "Six-digit OTP code from the email", example = "123456")
    @NotBlank(message = ValidationMessages.REQUIRED)
    @Pattern(regexp = "\\d{6}", message = "OTP code must be exactly 6 digits")
    private String code;

    @Schema(description = "Purpose the OTP was issued for", example = "EMAIL_VERIFICATION")
    @NotNull(message = ValidationMessages.REQUIRED)
    private OtpPurpose purpose;
}
