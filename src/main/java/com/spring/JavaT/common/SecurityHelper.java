package com.spring.JavaT.common;

import com.spring.JavaT.customer.Customer;
import com.spring.JavaT.customer.CustomerRepository;
import com.spring.JavaT.exception.ForbiddenException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private final CustomerRepository customerRepository;

    public Customer requireCustomerForUser(String userEmail) {
        return customerRepository.findByUser_Email(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for current user"));
    }

    public void ensureCustomerOwns(Long customerId, String userEmail) {
        Customer customer = requireCustomerForUser(userEmail);
        if (!customer.getId().equals(customerId)) {
            throw new ForbiddenException("You do not have access to this resource");
        }
    }
}
