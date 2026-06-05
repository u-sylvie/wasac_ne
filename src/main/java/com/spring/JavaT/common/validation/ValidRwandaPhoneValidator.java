package com.spring.JavaT.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class ValidRwandaPhoneValidator implements ConstraintValidator<ValidRwandaPhone, String> {

    private static final Pattern RWANDA_PHONE = Pattern.compile("^(07[2389])[0-9]{7}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return RWANDA_PHONE.matcher(value.trim()).matches();
    }
}
