package com.spring.JavaT.customer;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.auth.dto.RegisterRequest;
import com.spring.JavaT.customer.dto.CustomerCreateRequest;
import com.spring.JavaT.customer.dto.CustomerResponse;
import com.spring.JavaT.customer.dto.CustomerUpdateRequest;
import com.spring.JavaT.exception.DuplicateResourceException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import com.spring.JavaT.user.User;
import com.spring.JavaT.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.spring.JavaT.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public Page<CustomerResponse> getAll(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<Customer> spec = new BaseSpecification<>(criteria);
        return customerRepository.findAll(spec, pageable).map(CustomerMapper::toResponse);
    }

    public CustomerResponse getById(Long id) {
        return CustomerMapper.toResponse(findOrThrow(id));
    }

    public CustomerResponse getByNationalId(String nationalId) {
        String normalized = normalizeNationalId(nationalId);
        return CustomerMapper.toResponse(
                customerRepository.findByNationalId(normalized)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer", "nationalId", normalized)));
    }

    /**
     * Ensures National ID, email, and phone are not already used by another customer
     * before a self-registration completes.
     */
    public void validateAvailableForRegistration(RegisterRequest request) {
        validateDuplicates(
                normalizeNationalId(request.getNationalId()),
                request.getEmail().strip().toLowerCase(),
                request.getPhone().strip(),
                null);
        validateAge(request.getDateOfBirth());
    }

    /**
     * Creates a billing customer profile linked to a newly registered CUSTOMER user.
     * Called automatically from {@link com.spring.JavaT.auth.AuthService#register}.
     */
    @Transactional
    public Customer createFromSelfRegistration(User user, RegisterRequest request) {
        String nationalId = normalizeNationalId(request.getNationalId());
        String fullName = request.getFirstName().strip() + " " + request.getLastName().strip();
        Customer customer = new Customer();
        apply(customer, fullName, nationalId,
                request.getEmail().strip().toLowerCase(),
                request.getPhone().strip(),
                request.getAddress().strip(),
                request.getDateOfBirth(),
                user.getId());
        Customer saved = customerRepository.save(customer);
        auditService.log("Customer", saved.getId(), "CREATE", user.getEmail(),
                "Self-registration: customer " + saved.getNationalId() + " linked to user " + user.getEmail());
        return saved;
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request, String actor) {
        String nationalId = normalizeNationalId(request.getNationalId());
        validateDuplicates(nationalId, request.getEmail(), request.getPhone(), null);
        validateAge(request.getDateOfBirth());
        Customer customer = new Customer();
        apply(customer, request.getFullName(), nationalId, request.getEmail(),
                request.getPhone(), request.getAddress(), request.getDateOfBirth(), request.getUserId());
        Customer saved = customerRepository.save(customer);
        auditService.log("Customer", saved.getId(), "CREATE", actor,
                "Created customer NID=" + saved.getNationalId() + " email=" + saved.getEmail());
        return CustomerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest request, String actor) {
        Customer customer = findOrThrow(id);
        String nationalId = request.getNationalId() != null
                ? normalizeNationalId(request.getNationalId())
                : customer.getNationalId();
        validateDuplicates(
                nationalId,
                request.getEmail() != null ? request.getEmail() : customer.getEmail(),
                request.getPhone() != null ? request.getPhone() : customer.getPhone(),
                id);
        LocalDate dob = request.getDateOfBirth() != null ? request.getDateOfBirth() : customer.getDateOfBirth();
        validateAge(dob);
        apply(customer,
                request.getFullName() != null ? request.getFullName() : customer.getFullName(),
                nationalId,
                request.getEmail() != null ? request.getEmail() : customer.getEmail(),
                request.getPhone() != null ? request.getPhone() : customer.getPhone(),
                request.getAddress() != null ? request.getAddress() : customer.getAddress(),
                dob,
                request.getUserId() != null ? request.getUserId() : (customer.getUser() != null ? customer.getUser().getId() : null));
        Customer saved = customerRepository.save(customer);
        auditService.log("Customer", saved.getId(), "UPDATE", actor, "Updated customer " + saved.getEmail());
        return CustomerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse deactivate(Long id, String actor) {
        Customer customer = findOrThrow(id);
        customer.softDelete(actor);
        Customer saved = customerRepository.save(customer);
        auditService.log("Customer", saved.getId(), "UPDATE", actor, "Deactivated customer " + saved.getEmail());
        log.info("Customer {} deactivated by {}", saved.getEmail(), actor);
        return CustomerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse activate(Long id, String actor) {
        Customer customer = findOrThrow(id);
        customer.restore();
        Customer saved = customerRepository.save(customer);
        auditService.log("Customer", saved.getId(), "UPDATE", actor, "Activated customer " + saved.getEmail());
        log.info("Customer {} activated by {}", saved.getEmail(), actor);
        return CustomerMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id, String actor) {
        Customer customer = findOrThrow(id);
        customerRepository.delete(customer);
        auditService.log("Customer", id, "DELETE", actor, "Deleted customer " + customer.getEmail());
    }

    private void apply(Customer customer, String fullName, String nationalId, String email,
                       String phone, String address, LocalDate dateOfBirth, Long userId) {
        customer.setFullName(fullName);
        customer.setNationalId(nationalId);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setAddress(address);
        customer.setDateOfBirth(dateOfBirth);
        customer.setUser(userId != null ? getUser(userId) : null);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void validateDuplicates(String nationalId, String email, String phone, Long currentId) {
        if (nationalId != null) {
            customerRepository.findByNationalId(nationalId).ifPresent(c -> {
                if (currentId == null || !c.getId().equals(currentId)) {
                    throw new DuplicateResourceException("Customer", "nationalId", nationalId);
                }
            });
        }
        if (email != null) {
            customerRepository.findByEmail(email).ifPresent(c -> {
                if (currentId == null || !c.getId().equals(currentId)) {
                    throw new DuplicateResourceException("Customer", "email", email);
                }
            });
        }
        if (phone != null) {
            customerRepository.findByPhone(phone).ifPresent(c -> {
                if (currentId == null || !c.getId().equals(currentId)) {
                    throw new DuplicateResourceException("Customer", "phone", phone);
                }
            });
        }
    }

    private void validateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return;
        }
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (age < 18) {
            throw new BusinessException("Customer must be at least 18 years old", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeNationalId(String nationalId) {
        if (nationalId == null) {
            return null;
        }
        return nationalId.replaceAll("\\s+", "").trim();
    }

    /**
     * Loads a customer for meter/bill assignment.
     * Detects the common mistake of passing a {@code users.id} instead of a {@code customers.id}.
     */
    public Customer requireForAssignment(Long customerId) {
        return customerRepository.findById(customerId).orElseThrow(() -> {
            if (userRepository.existsById(customerId)) {
                throw new BusinessException(
                        "ID " + customerId + " is a user account ID, not a billing customer ID. "
                                + "Use the customerId returned from POST /api/v1/auth/register, "
                                + "or GET /api/v1/customers for an existing billing profile.",
                        HttpStatus.BAD_REQUEST,
                        "USER_ID_NOT_CUSTOMER_ID");
            }
            return new ResourceNotFoundException("Customer", "id", customerId);
        });
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
    }
}
