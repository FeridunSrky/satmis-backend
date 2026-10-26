package com.satmis.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// The upper bound moves every year, so a plain @Max cannot express it
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ModelYearValidator.class)
public @interface ModelYear {

    String message() default "Model yılı 1950 ile gelecek yıl arasında olmalıdır";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
