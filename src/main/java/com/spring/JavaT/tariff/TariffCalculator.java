package com.spring.JavaT.tariff;

import com.spring.JavaT.common.TariffType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

@Component
public class TariffCalculator {

    public BigDecimal calculateConsumptionCharge(TariffConfig tariff, BigDecimal consumption) {
        if (tariff.getTariffType() == TariffType.FLAT) {
            return consumption.multiply(tariff.getRatePerUnit()).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal remaining = consumption;
        BigDecimal charge = BigDecimal.ZERO;

        for (TariffTier tier : tariff.getTiers().stream()
                .sorted(Comparator.comparing(TariffTier::getFromUnits))
                .toList()) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal tierCapacity = tier.getToUnits() == null
                    ? remaining
                    : tier.getToUnits().subtract(tier.getFromUnits());

            BigDecimal unitsInTier = remaining.min(tierCapacity);
            charge = charge.add(unitsInTier.multiply(tier.getRatePerUnit()));
            remaining = remaining.subtract(unitsInTier);
        }

        return charge.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTax(BigDecimal subtotal, BigDecimal vatPercentage) {
        return subtotal.multiply(vatPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePenalty(BigDecimal subtotal, BigDecimal penaltyPercentage) {
        return subtotal.multiply(penaltyPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
