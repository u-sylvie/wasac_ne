package com.spring.JavaT.meter.dto;

import com.spring.JavaT.common.EntityStatus;
import com.spring.JavaT.common.MeterType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class MeterResponse {
    Long id;
    Long customerId;
    String customerName;
    String meterNumber;
    MeterType meterType;
    LocalDate installationDate;
    EntityStatus status;
}
