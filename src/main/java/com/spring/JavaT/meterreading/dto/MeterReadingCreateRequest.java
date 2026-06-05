package com.spring.JavaT.meterreading.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MeterReadingCreateRequest {
    @NotNull
    private Long meterId;
    @NotNull
    private BigDecimal previousReading;
    @NotNull
    private BigDecimal currentReading;
    @NotNull
    private LocalDate readingDate;
    @NotNull
    private Integer billingYear;
    @NotNull
    private Integer billingMonth;
}
