package com.spring.JavaT.tariff;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.TariffType;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.exception.BusinessException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import com.spring.JavaT.tariff.dto.TariffCreateRequest;
import com.spring.JavaT.tariff.dto.TariffResponse;
import com.spring.JavaT.tariff.dto.TariffTierRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.spring.JavaT.common.validation.TariffTierValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TariffService {

    private final TariffConfigRepository tariffConfigRepository;
    private final AuditService auditService;

    public Page<TariffResponse> getAll(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<TariffConfig> spec = new BaseSpecification<>(criteria);
        return tariffConfigRepository.findAll(spec, pageable).map(TariffMapper::toResponse);
    }

    public TariffResponse getById(Long id) {
        return TariffMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public TariffResponse create(TariffCreateRequest request, String actor) {
        validateRequest(request);

        if (request.getEffectiveFrom().isBefore(LocalDate.now())) {
            throw new BusinessException(
                    "Effective date cannot be in the past",
                    HttpStatus.BAD_REQUEST);
        }

        Integer nextVersion = tariffConfigRepository.findMaxVersionByMeterType(request.getMeterType()) + 1;

        TariffConfig tariff = TariffConfig.builder()
                .name(request.getName())
                .meterType(request.getMeterType())
                .tariffType(request.getTariffType())
                .ratePerUnit(request.getRatePerUnit())
                .fixedServiceCharge(request.getFixedServiceCharge())
                .vatPercentage(request.getVatPercentage())
                .penaltyPercentage(request.getPenaltyPercentage())
                .version(nextVersion)
                .effectiveFrom(request.getEffectiveFrom())
                .active(true)
                .tiers(new ArrayList<>())
                .build();

        if (request.getTariffType() == TariffType.TIERED) {
            for (TariffTierRequest tierRequest : request.getTiers()) {
                TariffTier tier = TariffTier.builder()
                        .tariffConfig(tariff)
                        .fromUnits(tierRequest.getFromUnits())
                        .toUnits(tierRequest.getToUnits())
                        .ratePerUnit(tierRequest.getRatePerUnit())
                        .build();
                tariff.getTiers().add(tier);
            }
        }

        TariffConfig saved = tariffConfigRepository.save(tariff);
        auditService.log("TariffConfig", saved.getId(), "CREATE", actor,
                "Created tariff version " + saved.getVersion() + " for " + saved.getMeterType());
        log.info("Tariff version {} created for {}", saved.getVersion(), saved.getMeterType());
        return TariffMapper.toResponse(saved);
    }

    @Transactional
    public TariffResponse deactivate(Long id, String actor) {
        TariffConfig tariff = findOrThrow(id);
        tariff.setActive(false);
        TariffConfig saved = tariffConfigRepository.save(tariff);
        auditService.log("TariffConfig", saved.getId(), "UPDATE", actor, "Deactivated tariff version " + saved.getVersion());
        return TariffMapper.toResponse(saved);
    }

    public TariffConfig resolveActiveTariff(com.spring.JavaT.common.MeterType meterType, LocalDate billingDate) {
        return tariffConfigRepository
                .findTopByMeterTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByVersionDesc(meterType, billingDate)
                .orElseThrow(() -> new BusinessException(
                        "No active tariff found for meter type " + meterType + " on " + billingDate,
                        HttpStatus.BAD_REQUEST));
    }

    private void validateRequest(TariffCreateRequest request) {
        if (request.getTariffType() == TariffType.FLAT) {
            if (request.getRatePerUnit() == null) {
                throw new BusinessException("Flat tariff requires ratePerUnit", HttpStatus.BAD_REQUEST);
            }
            if (request.getRatePerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Price per unit must be greater than zero", HttpStatus.BAD_REQUEST);
            }
        }
        if (request.getTariffType() == TariffType.TIERED
                && (request.getTiers() == null || request.getTiers().isEmpty())) {
            throw new BusinessException("Tiered tariff requires at least one tier", HttpStatus.BAD_REQUEST);
        }
        if (request.getVatPercentage().compareTo(BigDecimal.ZERO) < 0
                || request.getVatPercentage().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("VAT must be between 0 and 100 percent", HttpStatus.BAD_REQUEST);
        }
        if (request.getFixedServiceCharge().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Service charge cannot be negative", HttpStatus.BAD_REQUEST);
        }
        if (request.getPenaltyPercentage().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Late penalty cannot be negative", HttpStatus.BAD_REQUEST);
        }
        if (request.getTariffType() == TariffType.TIERED && request.getTiers() != null) {
            try {
                TariffTierValidator.validateNoOverlap(request.getTiers());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ex.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }
    }

    private TariffConfig findOrThrow(Long id) {
        return tariffConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TariffConfig", "id", id));
    }
}
