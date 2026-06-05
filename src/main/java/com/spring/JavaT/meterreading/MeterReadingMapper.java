package com.spring.JavaT.meterreading;

import com.spring.JavaT.meterreading.dto.MeterReadingResponse;

public final class MeterReadingMapper {
    private MeterReadingMapper() {}

    public static MeterReadingResponse toResponse(MeterReading reading) {
        return MeterReadingResponse.builder()
                .id(reading.getId())
                .meterId(reading.getMeter().getId())
                .meterNumber(reading.getMeter().getMeterNumber())
                .previousReading(reading.getPreviousReading())
                .currentReading(reading.getCurrentReading())
                .readingDate(reading.getReadingDate())
                .billingYear(reading.getBillingYear())
                .billingMonth(reading.getBillingMonth())
                .status(reading.getStatus())
                .build();
    }
}
