package com.satmis.listing;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Raw input never reaches to_tsquery: only letter/digit runs survive, so tsquery operators cannot be injected
final class KeywordQuery {

    static final int MAX_TERMS = 5;

    private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}]+");

    private KeywordQuery() {
    }

    // "Passat 1.6 dizel" -> "'Passat':* & '1':* & '6':* & 'dizel':*"; lowercasing and accents are left to PostgreSQL
    static Optional<String> toTsQuery(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String tsQuery = TERM.matcher(input).results()
                .map(match -> "'" + match.group() + "':*")
                .distinct()
                .limit(MAX_TERMS)
                .collect(Collectors.joining(" & "));
        return tsQuery.isEmpty() ? Optional.empty() : Optional.of(tsQuery);
    }
}
