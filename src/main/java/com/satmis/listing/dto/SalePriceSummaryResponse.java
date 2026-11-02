package com.satmis.listing.dto;

import com.satmis.enums.CurrencyType;

import java.math.BigDecimal;
import java.util.List;

// One entry per currency: averaging TRY and EUR prices together would be meaningless. Empty when nothing matches.
public record SalePriceSummaryResponse(List<CurrencyStats> currencies) {

    public record CurrencyStats(CurrencyType currency, long count, BigDecimal average, BigDecimal min,
                                BigDecimal max) {
    }
}
