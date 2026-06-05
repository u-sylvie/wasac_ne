package com.spring.JavaT.common.validation;

import com.spring.JavaT.tariff.dto.TariffTierRequest;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public final class TariffTierValidator {

    private TariffTierValidator() {}

    public static void validateNoOverlap(List<TariffTierRequest> tiers) {
        if (tiers == null || tiers.size() < 2) {
            return;
        }

        List<TariffTierRequest> sorted = tiers.stream()
                .sorted(Comparator.comparing(TariffTierRequest::getFromUnits))
                .toList();

        for (int i = 0; i < sorted.size() - 1; i++) {
            TariffTierRequest current = sorted.get(i);
            TariffTierRequest next = sorted.get(i + 1);

            BigDecimal currentEnd = current.getToUnits() != null ? current.getToUnits() : current.getFromUnits();
            if (next.getFromUnits().compareTo(currentEnd) <= 0) {
                throw new IllegalArgumentException(
                        "Tariff tier ranges must not overlap. Tier ending at "
                                + currentEnd + " overlaps with tier starting at " + next.getFromUnits());
            }
        }
    }
}
