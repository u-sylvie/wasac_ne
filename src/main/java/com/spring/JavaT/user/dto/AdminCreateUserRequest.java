package com.spring.JavaT.user.dto;

import com.spring.JavaT.common.validation.ValidEnum;
import com.spring.JavaT.common.validation.ValidRwandaPhone;
import com.spring.JavaT.common.validation.ValidationMessages;
import com.spring.JavaT.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Admin request to create an operator or finance user")
public class AdminCreateUserRequest {

    @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
    @Size(min = 2, max = 50)
    private String firstName;

    @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
    @Size(min = 2, max = 50)
    private String lastName;

    @NotBlank(message = ValidationMessages.EMAIL_REQUIRED)
    @Email(message = ValidationMessages.EMAIL_INVALID)
    private String email;

    @NotBlank
    @ValidRwandaPhone
    private String phone;

    @NotBlank(message = ValidationMessages.USERNAME_REQUIRED)
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "Role is required")
    @ValidEnum(enumClass = Role.class, message = "Role must be OPERATOR or FINANCE")
    private String role;
}
