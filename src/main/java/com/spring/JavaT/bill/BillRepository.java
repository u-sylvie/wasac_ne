package com.spring.JavaT.bill;

import com.spring.JavaT.common.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long>, JpaSpecificationExecutor<Bill> {

    Optional<Bill> findByReference(String reference);

    List<Bill> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Bill> findByBillStatus(BillStatus billStatus);

    boolean existsByMeterReadingId(Long meterReadingId);

    boolean existsByMeterIdAndBillingYearAndBillingMonth(Long meterId, Integer billingYear, Integer billingMonth);

    List<Bill> findByBillStatusInAndOutstandingBalanceGreaterThan(Collection<BillStatus> statuses, BigDecimal balance);
}
