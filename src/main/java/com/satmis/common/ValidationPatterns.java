package com.satmis.common;

public final class ValidationPatterns {

    public static final String PHONE = "^\\+?[0-9]{10,15}$";
    public static final String HTTPS_URL = "^https://\\S+$";

    private ValidationPatterns() {
    }
}
