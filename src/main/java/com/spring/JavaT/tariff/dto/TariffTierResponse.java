package com.spring.JavaT.tariff.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class TariffTierResponse {
    Long id;
    BigDecimal fromUnits;
    BigDecimal toUnits;
    BigDecimal ratePerUnit;
}
