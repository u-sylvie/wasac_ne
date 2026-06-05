package com.spring.JavaT.auth.dto;

import com.spring.JavaT.common.validation.NoWhitespace;
import com.spring.JavaT.common.validation.ValidPassword;
import com.spring.JavaT.common.validation.ValidRwandaNationalId;
import com.spring.JavaT.common.validation.ValidRwandaPhone;
import com.spring.JavaT.common.validation.ValidationGroups;
import com.spring.JavaT.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Self-registration for utility billing customers.
 *
 * <p>Creates both a login account ({@code users}) and a billing profile ({@code customers})
 * in one step, linked by National ID. Admin no longer needs to create the customer separately.
 */
@Getter
@Setter
@Schema(description = "Self-registration — creates user + billing customer profile")
public class RegisterRequest {

    @Schema(description = "First name", example = "Marie")
    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED, groups = ValidationGroups.OnCreate.class)
    @Size(min = 2, max = 50, groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String firstName;

    @Schema(description = "Last name", example = "Uwera")
    @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED, groups = ValidationGroups.OnCreate.class)
    @Size(min = 2, max = 50, groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String lastName;

    @Schema(description = "Rwanda National ID — 16 digits, must be unique", example = "119998877665544")
    @NotBlank(message = "National ID is required", groups = ValidationGroups.OnCreate.class)
    @ValidRwandaNationalId(groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String nationalId;

    @Schema(description = "Email address", example = "marie@example.com")
    @NotBlank(message = ValidationMessages.EMAIL_REQUIRED, groups = ValidationGroups.OnCreate.class)
    @Email(message = ValidationMessages.EMAIL_INVALID, groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    @Size(max = 254, message = ValidationMessages.EMAIL_TOO_LONG, groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String email;

    @Schema(description = "Rwanda phone number", example = "0788123456")
    @NotBlank(message = ValidationMessages.NOT_BLANK, groups = ValidationGroups.OnCreate.class)
    @ValidRwandaPhone(groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String phone;

    @Schema(description = "Service address", example = "Kigali, Gasabo")
    @NotBlank(message = "Address is required", groups = ValidationGroups.OnCreate.class)
    @Size(max = 255, groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String address;

    @Schema(description = "Date of birth (optional, must be 18+ if provided)", example = "1995-03-15")
    @Past(groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private LocalDate dateOfBirth;

    @Schema(description = "Unique username", example = "marieuwera")
    @NotBlank(message = ValidationMessages.USERNAME_REQUIRED, groups = ValidationGroups.OnCreate.class)
    @Size(min = 3, max = 50, groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    @NoWhitespace(groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String username;

    @Schema(description = "Password", example = "Secret@123")
    @NotBlank(message = ValidationMessages.PASSWORD_REQUIRED, groups = ValidationGroups.OnCreate.class)
    @ValidPassword(groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class})
    private String password;
}
