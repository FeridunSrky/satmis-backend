package com.satmis.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Validates every element of a List<String>. Used instead of List<@Pattern String> because the
// Eclipse compiler (STS auto-build) drops the generic signature of record components, which makes
// element-level constraints silently disappear.
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpsUrlListValidator.class)
public @interface HttpsUrlList {

    String message() default "Görsel adresleri https:// ile başlamalı ve en fazla 500 karakter olmalıdır";

    int maxLength() default 500;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
