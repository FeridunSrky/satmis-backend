package com.satmis.common.validation;

import com.satmis.common.ValidationPatterns;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.regex.Pattern;

public class HttpsUrlListValidator implements ConstraintValidator<HttpsUrlList, List<String>> {

    private static final Pattern HTTPS_URL = Pattern.compile(ValidationPatterns.HTTPS_URL);

    private int maxLength;

    @Override
    public void initialize(HttpsUrlList constraint) {
        this.maxLength = constraint.maxLength();
    }

    // Null list is left to @NotNull; blank or null elements fail the pattern
    @Override
    public boolean isValid(List<String> urls, ConstraintValidatorContext context) {
        if (urls == null) {
            return true;
        }
        return urls.stream().allMatch(this::isValidUrl);
    }

    private boolean isValidUrl(String url) {
        return url != null && url.length() <= maxLength && HTTPS_URL.matcher(url).matches();
    }
}
