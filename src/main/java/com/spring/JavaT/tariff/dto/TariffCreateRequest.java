package com.spring.JavaT.tariff.dto;

import com.spring.JavaT.common.MeterType;
import com.spring.JavaT.common.TariffType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TariffCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private MeterType meterType;

    @NotNull
    private TariffType tariffType;

    private BigDecimal ratePerUnit;

    @NotNull
    private BigDecimal fixedServiceCharge;

    @NotNull
    private BigDecimal vatPercentage;

    @NotNull
    private BigDecimal penaltyPercentage;

    @NotNull
    private LocalDate effectiveFrom;

    @Valid
    private List<TariffTierRequest> tiers = new ArrayList<>();
}
