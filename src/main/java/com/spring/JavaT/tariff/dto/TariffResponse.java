package com.spring.JavaT.tariff.dto;

import com.spring.JavaT.common.EntityStatus;
import com.spring.JavaT.common.MeterType;
import com.spring.JavaT.common.TariffType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class TariffResponse {
    Long id;
    String name;
    MeterType meterType;
    TariffType tariffType;
    BigDecimal ratePerUnit;
    BigDecimal fixedServiceCharge;
    BigDecimal vatPercentage;
    BigDecimal penaltyPercentage;
    Integer version;
    LocalDate effectiveFrom;
    boolean active;
    EntityStatus status;
    List<TariffTierResponse> tiers;
}
