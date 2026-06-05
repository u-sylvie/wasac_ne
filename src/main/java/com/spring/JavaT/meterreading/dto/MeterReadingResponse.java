package com.spring.JavaT.meterreading.dto;

import com.spring.JavaT.common.EntityStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class MeterReadingResponse {
    Long id;
    Long meterId;
    String meterNumber;
    BigDecimal previousReading;
    BigDecimal currentReading;
    LocalDate readingDate;
    Integer billingYear;
    Integer billingMonth;
    EntityStatus status;
}
