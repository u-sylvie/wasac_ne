package com.spring.JavaT.meter.dto;

import com.spring.JavaT.common.MeterType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MeterUpdateRequest {
    private Long customerId;
    private String meterNumber;
    private MeterType meterType;
    private LocalDate installationDate;
}
