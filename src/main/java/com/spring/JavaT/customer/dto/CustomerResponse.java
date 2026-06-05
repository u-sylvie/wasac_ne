package com.spring.JavaT.customer.dto;

import com.spring.JavaT.common.EntityStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class CustomerResponse {
    Long id;
    String fullName;
    String nationalId;
    String email;
    String phone;
    String address;
    LocalDate dateOfBirth;
    Long userId;
    EntityStatus status;
}
