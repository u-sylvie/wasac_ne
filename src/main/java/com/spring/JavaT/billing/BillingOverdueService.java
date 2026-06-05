package com.spring.JavaT.billing;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.bill.Bill;
import com.spring.JavaT.bill.BillRepository;
import com.spring.JavaT.common.BillStatus;
import com.spring.JavaT.common.EntityStatus;
import com.spring.JavaT.meter.Meter;
import com.spring.JavaT.meter.MeterRepository;
import com.spring.JavaT.tariff.TariffCalculator;
import com.spring.JavaT.tariff.TariffConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Applies late-payment penalties and disconnects meters for prolonged non-payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingOverdueService {

    private static final int PENALTY_AFTER_DAYS = 30;
    private static final int DISCONNECT_AFTER_DAYS = 60;

    private final BillRepository billRepository;
    private final MeterRepository meterRepository;
    private final TariffCalculator tariffCalculator;
    private final AuditService auditService;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void processOverdueBills() {
        LocalDate today = LocalDate.now();
        List<Bill> overdueCandidates = billRepository.findByBillStatusInAndOutstandingBalanceGreaterThan(
                List.of(BillStatus.UNPAID, BillStatus.APPROVED, BillStatus.PARTIALLY_PAID, BillStatus.OVERDUE),
                BigDecimal.ZERO);

        for (Bill bill : overdueCandidates) {
            long daysOverdue = today.toEpochDay() - bill.getDueDate().toEpochDay();
            if (daysOverdue < PENALTY_AFTER_DAYS) {
                continue;
            }

            if (bill.getBillStatus() != BillStatus.OVERDUE) {
                bill.setBillStatus(BillStatus.OVERDUE);
            }

            TariffConfig tariff = bill.getTariffConfig();
            if (tariff.getPenaltyPercentage().compareTo(BigDecimal.ZERO) > 0 && bill.getPenaltyAmount().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal subtotal = bill.getConsumptionCharge().add(bill.getFixedCharge());
                BigDecimal penalty = tariffCalculator.calculatePenalty(subtotal, tariff.getPenaltyPercentage());
                bill.setPenaltyAmount(penalty);
                bill.setTotalAmount(bill.getTotalAmount().add(penalty));
                bill.setOutstandingBalance(bill.getOutstandingBalance().add(penalty));
                auditService.log("Bill", bill.getId(), "UPDATE", "system",
                        "Applied late penalty of " + penalty + " FRW after " + daysOverdue + " days overdue");
            }

            if (daysOverdue >= DISCONNECT_AFTER_DAYS) {
                Meter meter = bill.getMeter();
                if (meter.getStatus() != EntityStatus.DISCONNECTED) {
                    meter.setStatus(EntityStatus.DISCONNECTED);
                    meterRepository.save(meter);
                    auditService.log("Meter", meter.getId(), "UPDATE", "system",
                            "Disconnected meter " + meter.getMeterNumber() + " due to unpaid bill " + bill.getReference());
                    log.warn("Meter {} disconnected — bill {} overdue {} days", meter.getMeterNumber(), bill.getReference(), daysOverdue);
                }
            }

            billRepository.save(bill);
        }
    }
}
