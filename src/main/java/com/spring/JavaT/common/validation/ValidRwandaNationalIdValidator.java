package com.spring.JavaT.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class ValidRwandaNationalIdValidator implements ConstraintValidator<ValidRwandaNationalId, String> {

    private static final Pattern RWANDA_NATIONAL_ID = Pattern.compile("^[0-9]{16}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return RWANDA_NATIONAL_ID.matcher(value.trim()).matches();
    }
}
