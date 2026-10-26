package com.satmis;

import java.security.SecureRandom;
import java.util.Base64;

// Each test run gets a fresh signing key so no secret is ever committed
public final class TestJwtSecret {

    private static final int SECRET_BYTE_LENGTH = 32;

    private TestJwtSecret() {
    }

    public static String random() {
        byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
