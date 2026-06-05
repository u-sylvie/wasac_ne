package com.spring.JavaT.auth.dto;

import com.spring.JavaT.common.validation.NoWhitespace;
import com.spring.JavaT.common.validation.ValidPassword;
import com.spring.JavaT.common.validation.ValidationGroups;
import com.spring.JavaT.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for the user registration endpoint.
 *
 * <p>Demonstrates the full validation layer:
 * <ul>
 *   <li>Standard Bean Validation annotations with messages from {@link ValidationMessages}</li>
 *   <li>Custom {@link ValidPassword} constraint</li>
 *   <li>Custom {@link NoWhitespace} constraint</li>
 *   <li>{@link ValidationGroups} for group-based validation</li>
 * </ul>
 *
 * <p>Controller usage:
 * <pre>
 * {@literal @}PostMapping("/register")
 * public ResponseEntity&lt;?&gt; register(
 *         {@literal @}Validated(ValidationGroups.OnCreate.class) {@literal @}RequestBody RegisterRequest body,
 *         HttpServletRequest request) { ... }
 * </pre>
 */
@Getter
@Setter
@Schema(description = "Request body for user registration")
public class RegisterRequest {

    @Schema(description = "User's first name", example = "John")
    @NotBlank(
            message = ValidationMessages.FIRST_NAME_REQUIRED,
            groups  = ValidationGroups.OnCreate.class
    )
    @Size(
            min     = 2,
            max     = 50,
            message = "First name must be between 2 and 50 characters",
            groups  = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    private String firstName;

    @Schema(description = "User's last name", example = "Doe")
    @NotBlank(
            message = ValidationMessages.LAST_NAME_REQUIRED,
            groups  = ValidationGroups.OnCreate.class
    )
    @Size(
            min     = 2,
            max     = 50,
            message = "Last name must be between 2 and 50 characters",
            groups  = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    private String lastName;

    @Schema(description = "User's email address", example = "john.doe@example.com")
    @NotBlank(
            message = ValidationMessages.EMAIL_REQUIRED,
            groups  = ValidationGroups.OnCreate.class
    )
    @Email(
            message = ValidationMessages.EMAIL_INVALID,
            groups  = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    @Size(
            max     = 254,
            message = ValidationMessages.EMAIL_TOO_LONG,
            groups  = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    private String email;

    @Schema(description = "Phone number (unique format not enforced)", example = "+250788123456")
    @NotBlank(
            message = ValidationMessages.NOT_BLANK,
            groups  = ValidationGroups.OnCreate.class
    )
    @Pattern(
            regexp  = "^\\+?[0-9]{7,15}$",
            message = ValidationMessages.PHONE_INVALID,
            groups  = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    @Size(
            max     = 30,
            message = "Phone number must not exceed 30 characters",
            groups  = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    private String phone;

    @Schema(description = "Unique username (no spaces)", example = "johndoe")
    @NotBlank(
            message = ValidationMessages.USERNAME_REQUIRED,
            groups  = ValidationGroups.OnCreate.class
    )
    @Size(
            min     = 3,
            max     = 50,
            message = "Username must be between 3 and 50 characters",
            groups  = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    @NoWhitespace(
            groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    private String username;

    @Schema(description = "Password (min 8 chars, must include upper, lower, digit, special char)",
            example = "Secret@123")
    @NotBlank(
            message = ValidationMessages.PASSWORD_REQUIRED,
            groups  = ValidationGroups.OnCreate.class
    )
    @ValidPassword(
            groups = {ValidationGroups.OnCreate.class, ValidationGroups.OnUpdate.class}
    )
    private String password;
}
