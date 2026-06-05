package com.spring.JavaT.tariff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TariffTierRequest {

    @NotNull
    private BigDecimal fromUnits;

    private BigDecimal toUnits;

    @NotNull
    private BigDecimal ratePerUnit;
}
