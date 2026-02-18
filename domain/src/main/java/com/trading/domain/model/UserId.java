package com.trading.domain.model;

/**
 * Unique identifier for a User.
 * Value object represented as a record.
 */
public record UserId(String value) {

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId value cannot be null or blank");
        }
    }

    /**
     * Creates a UserId from a string value.
     */
    public static UserId of(String value) {
        return new UserId(value);
    }
}
