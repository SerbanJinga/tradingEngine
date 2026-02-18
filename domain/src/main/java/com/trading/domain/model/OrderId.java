package com.trading.domain.model;

import java.util.UUID;

/**
 * Unique identifier for an Order.
 * Value object represented as a record.
 */
public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId value cannot be null or blank");
        }
    }

    /**
     * Generates a new unique OrderId.
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }
}
