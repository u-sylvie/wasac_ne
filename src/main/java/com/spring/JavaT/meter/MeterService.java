package com.spring.JavaT.meter;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.customer.Customer;
import com.spring.JavaT.customer.CustomerService;
import com.spring.JavaT.common.validation.MeterNumberValidator;
import com.spring.JavaT.exception.DuplicateResourceException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import com.spring.JavaT.meter.dto.MeterCreateRequest;
import com.spring.JavaT.meter.dto.MeterResponse;
import com.spring.JavaT.meter.dto.MeterUpdateRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeterService {
    private final MeterRepository meterRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public Page<MeterResponse> getAll(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<Meter> spec = new BaseSpecification<>(criteria);
        return meterRepository.findAll(spec, pageable).map(MeterMapper::toResponse);
    }

    public MeterResponse getById(Long id) {
        return MeterMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public MeterResponse create(MeterCreateRequest request, String actor) {
        ensureUniqueMeterNumber(request.getMeterNumber(), null);
        Meter meter = new Meter();
        apply(meter, request.getCustomerId(), request.getMeterNumber(), request.getMeterType(), request.getInstallationDate());
        Meter saved = meterRepository.save(meter);
        auditService.log("Meter", saved.getId(), "CREATE", actor, "Created meter " + saved.getMeterNumber());
        return MeterMapper.toResponse(saved);
    }

    @Transactional
    public MeterResponse update(Long id, MeterUpdateRequest request, String actor) {
        Meter meter = findOrThrow(id);
        if (request.getMeterNumber() != null) ensureUniqueMeterNumber(request.getMeterNumber(), id);
        apply(meter,
                request.getCustomerId() != null ? request.getCustomerId() : meter.getCustomer().getId(),
                request.getMeterNumber() != null ? request.getMeterNumber() : meter.getMeterNumber(),
                request.getMeterType() != null ? request.getMeterType() : meter.getMeterType(),
                request.getInstallationDate() != null ? request.getInstallationDate() : meter.getInstallationDate());
        Meter saved = meterRepository.save(meter);
        auditService.log("Meter", saved.getId(), "UPDATE", actor, "Updated meter " + saved.getMeterNumber());
        return MeterMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id, String actor) {
        Meter meter = findOrThrow(id);
        meterRepository.delete(meter);
        auditService.log("Meter", id, "DELETE", actor, "Deleted meter " + meter.getMeterNumber());
    }

    private void apply(Meter meter, Long customerId, String meterNumber, com.spring.JavaT.common.MeterType meterType, java.time.LocalDate installationDate) {
        // customerId must be from the customers table — not the users table
        meter.setCustomer(customerService.requireForAssignment(customerId));
        MeterNumberValidator.validate(meterType, meterNumber);
        meter.setMeterNumber(meterNumber.trim().toUpperCase());
        meter.setMeterType(meterType);
        meter.setInstallationDate(installationDate);
    }

    private void ensureUniqueMeterNumber(String meterNumber, Long id) {
        meterRepository.findByMeterNumber(meterNumber).ifPresent(existing -> {
            if (id == null || !existing.getId().equals(id)) {
                throw new DuplicateResourceException("Meter", "meterNumber", meterNumber);
            }
        });
    }

    private Meter findOrThrow(Long id) {
        return meterRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Meter", "id", id));
    }
}
