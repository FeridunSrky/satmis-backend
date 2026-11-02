package com.satmis.listing;

import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Sale prices are published as market data, so a made-up value (1 TL) must not get in. Bargaining usually lowers
// the price a little; a sale far above the asking price is just as unlikely.
final class SalePricePolicy {

    static final BigDecimal MIN_RATIO = new BigDecimal("0.80");
    static final BigDecimal MAX_RATIO = new BigDecimal("1.10");

    private SalePricePolicy() {
    }

    static void requireWithinRange(Listing listing, BigDecimal salePrice) {
        BigDecimal min = listing.getPrice().multiply(MIN_RATIO).setScale(2, RoundingMode.CEILING);
        BigDecimal max = listing.getPrice().multiply(MAX_RATIO).setScale(2, RoundingMode.FLOOR);
        if (salePrice.compareTo(min) < 0 || salePrice.compareTo(max) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SALE_PRICE_OUT_OF_RANGE,
                    "Satış fiyatı ilan fiyatının %%80'i ile %%110'u arasında olmalıdır (%s – %s %s)"
                            .formatted(plain(min), plain(max), listing.getCurrency()));
        }
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
