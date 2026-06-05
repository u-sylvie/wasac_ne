package com.spring.JavaT.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rwanda National ID — 16 numeric digits (e.g. 119998877665544).
 */
@Documented
@Constraint(validatedBy = ValidRwandaNationalIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRwandaNationalId {

    String message() default "National ID must be exactly 16 digits (Rwanda NID format)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
