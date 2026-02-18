package com.trading.domain.model;

import java.util.regex.Pattern;

/**
 * Trading symbol (e.g., "BTC-USD", "ETH-USDT").
 * Format: BASE-QUOTE where both are uppercase letters.
 * Value object represented as a record.
 */
public record Symbol(String value) {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z]+-[A-Z]+$");

    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol value cannot be null or blank");
        }
        if (!SYMBOL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Symbol must be in format BASE-QUOTE (e.g., BTC-USD). Got: " + value
            );
        }
    }

    /**
     * Creates a Symbol from a string value.
     */
    public static Symbol of(String value) {
        return new Symbol(value);
    }

    /**
     * Gets the base currency (e.g., "BTC" from "BTC-USD").
     */
    public String getBase() {
        return value.split("-")[0];
    }

    /**
     * Gets the quote currency (e.g., "USD" from "BTC-USD").
     */
    public String getQuote() {
        return value.split("-")[1];
    }
}
