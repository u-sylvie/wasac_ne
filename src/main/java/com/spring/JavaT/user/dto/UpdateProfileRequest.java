package com.spring.JavaT.user.dto;

import com.spring.JavaT.common.validation.NoWhitespace;
import com.spring.JavaT.common.validation.ValidationGroups;
import com.spring.JavaT.common.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for updating a user's own profile (PATCH /users/me).
 *
 * <p>All fields are optional — only non-null fields are applied.
 * Uses {@link ValidationGroups.OnPatch} so constraints only fire
 * when a field is actually provided.
 */
@Getter
@Setter
@Schema(description = "Fields available for the user to update on their own profile")
public class UpdateProfileRequest {

    @Schema(description = "New first name", example = "Jane")
    @Size(
            min     = 2,
            max     = 50,
            message = "First name must be between 2 and 50 characters",
            groups  = ValidationGroups.OnPatch.class
    )
    private String firstName;

    @Schema(description = "New last name", example = "Smith")
    @Size(
            min     = 2,
            max     = 50,
            message = "Last name must be between 2 and 50 characters",
            groups  = ValidationGroups.OnPatch.class
    )
    private String lastName;

    @Schema(description = "New unique username", example = "janesmith")
    @Size(
            min     = 3,
            max     = 50,
            message = ValidationMessages.USERNAME_TOO_SHORT,
            groups  = ValidationGroups.OnPatch.class
    )
    @NoWhitespace(groups = ValidationGroups.OnPatch.class)
    private String username;

    @Schema(description = "New phone number", example = "+250788123456")
    @Pattern(
            regexp  = "^\\+?[0-9]{7,15}$",
            message = ValidationMessages.PHONE_INVALID,
            groups  = ValidationGroups.OnPatch.class
    )
    @Size(
            max     = 30,
            message = "Phone number must not exceed 30 characters",
            groups  = ValidationGroups.OnPatch.class
    )
    private String phone;
}
