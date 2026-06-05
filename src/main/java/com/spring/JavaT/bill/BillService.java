package com.spring.JavaT.bill;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.BillStatus;
import com.spring.JavaT.common.SecurityHelper;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.customer.Customer;
import com.spring.JavaT.exception.BusinessException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import com.spring.JavaT.meter.Meter;
import com.spring.JavaT.meterreading.MeterReading;
import com.spring.JavaT.meterreading.MeterReadingRepository;
import com.spring.JavaT.tariff.TariffCalculator;
import com.spring.JavaT.tariff.TariffConfig;
import com.spring.JavaT.tariff.TariffService;
import com.spring.JavaT.bill.dto.BillGenerateRequest;
import com.spring.JavaT.bill.dto.BillResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final TariffService tariffService;
    private final TariffCalculator tariffCalculator;
    private final AuditService auditService;
    private final SecurityHelper securityHelper;

    public Page<BillResponse> getAll(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<Bill> spec = new BaseSpecification<>(criteria);
        return billRepository.findAll(spec, pageable).map(BillMapper::toResponse);
    }

    public Page<BillResponse> getAllForCustomer(Long customerId, List<SearchCriteria> criteria, Pageable pageable) {
        List<SearchCriteria> allCriteria = new java.util.ArrayList<>(criteria);
        allCriteria.add(new SearchCriteria("customer.id", SearchCriteria.Op.EQ, customerId));
        return getAll(allCriteria, pageable);
    }

    public BillResponse getById(Long id) {
        return BillMapper.toResponse(findOrThrow(id));
    }

    public BillResponse getByIdForCustomer(Long id, String userEmail) {
        Bill bill = findOrThrow(id);
        securityHelper.ensureCustomerOwns(bill.getCustomer().getId(), userEmail);
        return BillMapper.toResponse(bill);
    }

    @Transactional
    public BillResponse generate(BillGenerateRequest request, String actor) {
        MeterReading reading = meterReadingRepository.findById(request.getMeterReadingId())
                .orElseThrow(() -> new ResourceNotFoundException("MeterReading", "id", request.getMeterReadingId()));

        if (billRepository.existsByMeterReadingId(reading.getId())) {
            throw new BusinessException("A bill already exists for this meter reading", HttpStatus.CONFLICT);
        }

        Meter meter = reading.getMeter();

        if (billRepository.existsByMeterIdAndBillingYearAndBillingMonth(
                meter.getId(), reading.getBillingYear(), reading.getBillingMonth())) {
            throw new BusinessException(
                    "A bill already exists for this meter and billing period", HttpStatus.CONFLICT);
        }
        Customer customer = meter.getCustomer();

        if (!customer.isActive()) {
            throw new BusinessException("Inactive customers cannot receive bills", HttpStatus.BAD_REQUEST);
        }

        LocalDate billingDate = LocalDate.of(reading.getBillingYear(), reading.getBillingMonth(), 1);
        TariffConfig tariff = tariffService.resolveActiveTariff(meter.getMeterType(), billingDate);

        BigDecimal consumption = reading.getCurrentReading().subtract(reading.getPreviousReading());
        if (consumption.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Consumption must be greater than zero to generate a bill", HttpStatus.BAD_REQUEST);
        }

        BigDecimal consumptionCharge = tariffCalculator.calculateConsumptionCharge(tariff, consumption);
        BigDecimal fixedCharge = tariff.getFixedServiceCharge();
        BigDecimal subtotal = consumptionCharge.add(fixedCharge);
        BigDecimal taxAmount = tariffCalculator.calculateTax(subtotal, tariff.getVatPercentage());
        BigDecimal penaltyAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(taxAmount).add(penaltyAmount);

        LocalDate dueDate = request.getDueDate() != null
                ? request.getDueDate()
                : reading.getReadingDate().plusDays(30);

        Bill bill = Bill.builder()
                .reference("BILL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customer(customer)
                .meter(meter)
                .meterReading(reading)
                .tariffConfig(tariff)
                .billingYear(reading.getBillingYear())
                .billingMonth(reading.getBillingMonth())
                .consumption(consumption)
                .consumptionCharge(consumptionCharge)
                .fixedCharge(fixedCharge)
                .taxAmount(taxAmount)
                .penaltyAmount(penaltyAmount)
                .totalAmount(totalAmount)
                .amountPaid(BigDecimal.ZERO)
                .outstandingBalance(totalAmount)
                .dueDate(dueDate)
                .billStatus(BillStatus.UNPAID)
                .build();

        Bill saved = billRepository.save(bill);
        auditService.log("Bill", saved.getId(), "CREATE", actor,
                "Generated bill " + saved.getReference() + " for customer " + customer.getEmail());
        log.info("Bill {} generated for customer {}", saved.getReference(), customer.getEmail());
        return BillMapper.toResponse(saved);
    }

    @Transactional
    public BillResponse approve(Long id, String actor) {
        Bill bill = findOrThrow(id);
        if (bill.getBillStatus() == BillStatus.APPROVED) {
            throw new BusinessException("Bill is already approved", HttpStatus.CONFLICT);
        }
        if (bill.getBillStatus() != BillStatus.UNPAID) {
            throw new BusinessException("Only unpaid bills can be approved", HttpStatus.BAD_REQUEST);
        }
        if (!bill.getCustomer().isActive()) {
            throw new BusinessException("Cannot approve bill for inactive customer", HttpStatus.BAD_REQUEST);
        }

        bill.setBillStatus(BillStatus.APPROVED);
        bill.setApprovedAt(Instant.now());
        bill.setApprovedBy(actor);

        Bill saved = billRepository.save(bill);
        auditService.log("Bill", saved.getId(), "UPDATE", actor, "Approved bill " + saved.getReference());
        return BillMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id, String actor) {
        Bill bill = findOrThrow(id);
        if (bill.getBillStatus() == BillStatus.PAID) {
            throw new BusinessException("Cannot delete a paid bill", HttpStatus.BAD_REQUEST);
        }
        billRepository.delete(bill);
        auditService.log("Bill", id, "DELETE", actor, "Deleted bill " + bill.getReference());
    }

    private Bill findOrThrow(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", "id", id));
    }
}
