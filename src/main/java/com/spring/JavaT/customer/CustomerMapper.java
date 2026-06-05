package com.spring.JavaT.customer;

import com.spring.JavaT.customer.dto.CustomerResponse;

public final class CustomerMapper {
    private CustomerMapper() {}

    public static CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .fullName(customer.getFullName())
                .nationalId(customer.getNationalId())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .dateOfBirth(customer.getDateOfBirth())
                .userId(customer.getUser() != null ? customer.getUser().getId() : null)
                .status(customer.getStatus())
                .build();
    }
}
