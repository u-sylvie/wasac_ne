package com.spring.JavaT.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    boolean existsByNationalId(String nationalId);

    boolean existsByEmail(String email);

    Optional<Customer> findByNationalId(String nationalId);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByUser_Email(String email);

    boolean existsByPhone(String phone);

    Optional<Customer> findByPhone(String phone);
}
