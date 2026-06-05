package com.spring.JavaT.meter;

import com.spring.JavaT.meter.dto.MeterResponse;

public final class MeterMapper {
    private MeterMapper() {}

    public static MeterResponse toResponse(Meter meter) {
        return MeterResponse.builder()
                .id(meter.getId())
                .customerId(meter.getCustomer().getId())
                .customerName(meter.getCustomer().getFullName())
                .meterNumber(meter.getMeterNumber())
                .meterType(meter.getMeterType())
                .installationDate(meter.getInstallationDate())
                .status(meter.getStatus())
                .build();
    }
}
