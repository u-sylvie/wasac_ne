package com.spring.JavaT.meterreading;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.EntityStatus;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.exception.BusinessException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import com.spring.JavaT.meter.Meter;
import com.spring.JavaT.meter.MeterRepository;
import com.spring.JavaT.meterreading.dto.MeterReadingCreateRequest;
import com.spring.JavaT.meterreading.dto.MeterReadingResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeterReadingService {

    private final MeterReadingRepository meterReadingRepository;
    private final MeterRepository meterRepository;
    private final AuditService auditService;

    public Page<MeterReadingResponse> getAll(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<MeterReading> spec = new BaseSpecification<>(criteria);
        return meterReadingRepository.findAll(spec, pageable).map(MeterReadingMapper::toResponse);
    }

    public MeterReadingResponse getById(Long id) {
        return MeterReadingMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public MeterReadingResponse create(MeterReadingCreateRequest request, String actor) {
        Meter meter = meterRepository.findById(request.getMeterId())
                .orElseThrow(() -> new ResourceNotFoundException("Meter", "id", request.getMeterId()));

        if (meter.getStatus() != EntityStatus.ACTIVE) {
            throw new BusinessException("Meter must be active for reading", HttpStatus.BAD_REQUEST);
        }
        if (meterReadingRepository.existsByMeterIdAndBillingYearAndBillingMonth(
                meter.getId(), request.getBillingYear(), request.getBillingMonth())) {
            throw new BusinessException("A reading for this meter and period already exists", HttpStatus.CONFLICT);
        }
        if (request.getCurrentReading().compareTo(request.getPreviousReading()) <= 0) {
            throw new BusinessException("Current reading must be greater than previous reading", HttpStatus.BAD_REQUEST);
        }
        if (request.getReadingDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Reading date cannot be in the future", HttpStatus.BAD_REQUEST);
        }

        MeterReading reading = MeterReading.builder()
                .meter(meter)
                .previousReading(request.getPreviousReading())
                .currentReading(request.getCurrentReading())
                .readingDate(request.getReadingDate())
                .billingYear(request.getBillingYear())
                .billingMonth(request.getBillingMonth())
                .build();

        MeterReading saved = meterReadingRepository.save(reading);
        auditService.log("MeterReading", saved.getId(), "CREATE", actor,
                "Created reading for meter " + meter.getMeterNumber());
        log.info("Meter reading created for meter {} period {}/{}", meter.getMeterNumber(),
                request.getBillingMonth(), request.getBillingYear());
        return MeterReadingMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id, String actor) {
        MeterReading reading = findOrThrow(id);
        meterReadingRepository.delete(reading);
        auditService.log("MeterReading", id, "DELETE", actor, "Deleted meter reading");
    }

    private MeterReading findOrThrow(Long id) {
        return meterReadingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MeterReading", "id", id));
    }
}
