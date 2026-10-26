package com.satmis.common;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    // Locale.ROOT: with a Turkish default locale "I" would lower-case to dotless "ı"
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
