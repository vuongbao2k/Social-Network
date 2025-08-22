package com.jb.identity_service.validator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DobValidator implements ConstraintValidator<DobConstraint, LocalDate> {

    private int min;

    @Override
    public void initialize(DobConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        this.min = constraintAnnotation.min();
    }

    @Override
    public boolean isValid(LocalDate localDate, ConstraintValidatorContext constraintValidatorContext) {
        if (localDate == null) {
            return true; // Null values are considered valid
        }
        return ChronoUnit.YEARS.between(localDate, LocalDate.now()) >= min;
    }
}
