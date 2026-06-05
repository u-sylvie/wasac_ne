package com.spring.JavaT.tariff;

import com.spring.JavaT.tariff.dto.TariffResponse;
import com.spring.JavaT.tariff.dto.TariffTierResponse;

import java.util.List;

public final class TariffMapper {

    private TariffMapper() {}

    public static TariffResponse toResponse(TariffConfig tariff) {
        List<TariffTierResponse> tiers = tariff.getTiers().stream()
                .map(t -> TariffTierResponse.builder()
                        .id(t.getId())
                        .fromUnits(t.getFromUnits())
                        .toUnits(t.getToUnits())
                        .ratePerUnit(t.getRatePerUnit())
                        .build())
                .toList();

        return TariffResponse.builder()
                .id(tariff.getId())
                .name(tariff.getName())
                .meterType(tariff.getMeterType())
                .tariffType(tariff.getTariffType())
                .ratePerUnit(tariff.getRatePerUnit())
                .fixedServiceCharge(tariff.getFixedServiceCharge())
                .vatPercentage(tariff.getVatPercentage())
                .penaltyPercentage(tariff.getPenaltyPercentage())
                .version(tariff.getVersion())
                .effectiveFrom(tariff.getEffectiveFrom())
                .active(tariff.isActive())
                .status(tariff.getStatus())
                .tiers(tiers)
                .build();
    }
}
