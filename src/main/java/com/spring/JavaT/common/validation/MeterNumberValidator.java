package com.spring.JavaT.common.validation;

import com.spring.JavaT.common.MeterType;
import com.spring.JavaT.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * Service-layer meter number validation (used when type/number are resolved from existing entity).
 */
public final class MeterNumberValidator {

    private static final Pattern WATER       = Pattern.compile("^WTR-[0-9]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ELECTRICITY = Pattern.compile("^ELC-[0-9]+$", Pattern.CASE_INSENSITIVE);

    private MeterNumberValidator() {}

    public static void validate(MeterType meterType, String meterNumber) {
        if (meterType == null || meterNumber == null || meterNumber.isBlank()) {
            return;
        }
        String normalized = meterNumber.trim().toUpperCase();
        boolean valid = switch (meterType) {
            case WATER -> WATER.matcher(normalized).matches();
            case ELECTRICITY -> ELECTRICITY.matcher(normalized).matches();
        };
        if (!valid) {
            String expected = meterType == MeterType.WATER ? "WTR-0001" : "ELC-0001";
            throw new BusinessException(
                    meterType + " meters must use format " + expected + " (got: " + meterNumber + ")",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
