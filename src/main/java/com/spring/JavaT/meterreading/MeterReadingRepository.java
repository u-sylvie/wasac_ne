package com.spring.JavaT.meterreading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, Long>, JpaSpecificationExecutor<MeterReading> {

    boolean existsByMeterIdAndBillingYearAndBillingMonth(Long meterId, Integer billingYear, Integer billingMonth);

    Optional<MeterReading> findTopByMeterIdOrderByReadingDateDescIdDesc(Long meterId);
}
