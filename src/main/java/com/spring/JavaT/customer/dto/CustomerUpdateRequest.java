package com.spring.JavaT.customer.dto;

import com.spring.JavaT.common.validation.ValidRwandaPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CustomerUpdateRequest {
    @Size(max = 150)
    private String fullName;
    @Size(max = 20)
    private String nationalId;
    @Email @Size(max = 254)
    private String email;
    @ValidRwandaPhone
    private String phone;
    @Size(max = 255)
    private String address;
    @Past
    private LocalDate dateOfBirth;
    private Long userId;
}
