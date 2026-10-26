package com.satmis.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Year;
import java.time.ZoneOffset;

public class ModelYearValidator implements ConstraintValidator<ModelYear, Integer> {

    private static final int MIN_MODEL_YEAR = 1950;

    @Override
    public boolean isValid(Integer modelYear, ConstraintValidatorContext context) {
        if (modelYear == null) {
            return true;
        }
        // Manufacturers sell next year's model from autumn onwards
        int maxModelYear = Year.now(ZoneOffset.UTC).getValue() + 1;
        return modelYear >= MIN_MODEL_YEAR && modelYear <= maxModelYear;
    }
}
