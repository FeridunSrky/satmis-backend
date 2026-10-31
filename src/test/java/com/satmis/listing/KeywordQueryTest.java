package com.satmis.listing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordQueryTest {

    @Test
    void toTsQuery_wordsAndNumbers_becomePrefixTermsJoinedWithAnd() {
        assertThat(KeywordQuery.toTsQuery("Passat 1.6 dizel"))
                .contains("'Passat':* & '1':* & '6':* & 'dizel':*");
    }

    @Test
    void toTsQuery_turkishLetters_areKeptAsTyped() {
        assertThat(KeywordQuery.toTsQuery("IŞIKLI çağdaş")).contains("'IŞIKLI':* & 'çağdaş':*");
    }

    @Test
    void toTsQuery_querySyntaxCharacters_areDropped() {
        assertThat(KeywordQuery.toTsQuery("golf:* | !(bmw) & 'x'")).contains("'golf':* & 'bmw':* & 'x':*");
    }

    @Test
    void toTsQuery_moreTermsThanLimit_keepsFirstDistinctTerms() {
        assertThat(KeywordQuery.toTsQuery("a a b c d e f g")).contains("'a':* & 'b':* & 'c':* & 'd':* & 'e':*");
    }

    @Test
    void toTsQuery_nullBlankOrSymbolsOnly_returnsEmpty() {
        assertThat(KeywordQuery.toTsQuery(null)).isEmpty();
        assertThat(KeywordQuery.toTsQuery("   ")).isEmpty();
        assertThat(KeywordQuery.toTsQuery("&|!():*'\\")).isEmpty();
    }
}
