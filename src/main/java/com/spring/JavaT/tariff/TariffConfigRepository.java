package com.spring.JavaT.tariff;

import com.spring.JavaT.common.MeterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface TariffConfigRepository extends JpaRepository<TariffConfig, Long>, JpaSpecificationExecutor<TariffConfig> {

    Optional<TariffConfig> findTopByMeterTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByVersionDesc(
            MeterType meterType, LocalDate effectiveDate);

    List<TariffConfig> findByMeterTypeOrderByVersionDesc(MeterType meterType);

    @Query("SELECT COALESCE(MAX(t.version), 0) FROM TariffConfig t WHERE t.meterType = :meterType")
    Integer findMaxVersionByMeterType(@Param("meterType") MeterType meterType);
}
