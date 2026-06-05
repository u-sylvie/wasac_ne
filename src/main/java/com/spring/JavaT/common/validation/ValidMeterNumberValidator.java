package com.spring.JavaT.common.validation;

import com.spring.JavaT.common.MeterType;
import com.spring.JavaT.meter.dto.MeterCreateRequest;
import com.spring.JavaT.meter.dto.MeterUpdateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates that water meters use WTR- prefix and electricity meters use ELC- prefix.
 */
public class ValidMeterNumberValidator implements ConstraintValidator<ValidMeterNumber, Object> {

    private static final Pattern WATER   = Pattern.compile("^WTR-[0-9]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ELECTRICITY = Pattern.compile("^ELC-[0-9]+$", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        MeterType type;
        String number;

        if (value instanceof MeterCreateRequest req) {
            type = req.getMeterType();
            number = req.getMeterNumber();
        } else if (value instanceof MeterUpdateRequest req) {
            type = req.getMeterType();
            number = req.getMeterNumber();
            if (type == null || number == null) {
                return true;
            }
        } else {
            return true;
        }

        if (type == null || number == null || number.isBlank()) {
            return true;
        }

        String normalized = number.trim().toUpperCase();
        boolean matches = switch (type) {
            case WATER -> WATER.matcher(normalized).matches();
            case ELECTRICITY -> ELECTRICITY.matcher(normalized).matches();
        };

        if (!matches) {
            context.disableDefaultConstraintViolation();
            String expected = type == MeterType.WATER ? "WTR-0001" : "ELC-0001";
            context.buildConstraintViolationWithTemplate(
                    type + " meters must use format " + expected + " (got: " + number + ")"
            ).addConstraintViolation();
            return false;
        }
        return true;
    }
}
