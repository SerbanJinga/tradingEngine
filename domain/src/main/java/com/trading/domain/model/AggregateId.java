package com.trading.domain.model;

/**
 * Unique identifier for aggregates in the event-sourced system.
 * Combines a value with a type for type-safe aggregate identification.
 * Value object represented as a record.
 */
public record AggregateId(String value, String type) {

    public AggregateId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AggregateId value cannot be null or blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("AggregateId type cannot be null or blank");
        }
    }

    /**
     * Creates an AggregateId for an OrderBook.
     */
    public static AggregateId forOrderBook(Symbol symbol) {
        return new AggregateId(symbol.value(), "OrderBook");
    }
}
