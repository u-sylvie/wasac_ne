package com.spring.JavaT.customer.dto;

import com.spring.JavaT.common.validation.ValidRwandaPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CustomerCreateRequest {
    @NotBlank @Size(max = 150)
    private String fullName;
    @NotBlank @Size(max = 20)
    private String nationalId;
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank
    @ValidRwandaPhone
    private String phone;
    @NotBlank @Size(max = 255)
    private String address;
    @Past
    private LocalDate dateOfBirth;
    private Long userId;
}
