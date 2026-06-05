package com.spring.JavaT.meter.dto;

import com.spring.JavaT.common.MeterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MeterCreateRequest {
    @NotNull
    private Long customerId;
    @NotBlank @Size(max = 50)
    private String meterNumber;
    @NotNull
    private MeterType meterType;
    @NotNull
    private LocalDate installationDate;
}
