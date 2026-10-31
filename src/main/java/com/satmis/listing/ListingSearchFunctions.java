package com.satmis.listing;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

// Registered via META-INF/services; JPQL/Criteria have no full-text operator of their own
public class ListingSearchFunctions implements FunctionContributor {

    static final String TEXT_MATCHES = "listing_text_matches";

    // to_tsvector(...) must stay identical to the idx_listings_search expression (V8) for the GIN index to apply
    static final String TEXT_MATCHES_SQL =
            "(to_tsvector('satmis_search', ?1) @@ to_tsquery('satmis_search', ?2))";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(TEXT_MATCHES, TEXT_MATCHES_SQL,
                functionContributions.getTypeConfiguration().getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.BOOLEAN));
    }
}
