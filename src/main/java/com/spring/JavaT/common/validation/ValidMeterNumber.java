package com.spring.JavaT.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ensures meter number prefix matches meter type:
 * WATER → WTR-####  |  ELECTRICITY → ELC-####
 */
@Documented
@Constraint(validatedBy = ValidMeterNumberValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMeterNumber {

    String message() default "Meter number must match meter type (WATER: WTR-####, ELECTRICITY: ELC-####)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
