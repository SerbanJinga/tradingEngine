package com.trading.domain.model;

import com.google.common.collect.ImmutableList;

/**
 * Represents the result of matching an order against an order book.
 * Contains the updated order and any trades that were executed.
 * Immutable result object represented as a record.
 */
public record MatchResult(
    Order order,
    ImmutableList<Trade> trades
) {

    public MatchResult {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (trades == null) {
            throw new IllegalArgumentException("Trades cannot be null");
        }
    }

    /**
     * Creates a MatchResult with no trades (order added to book without matching).
     */
    public static MatchResult noMatch(Order order) {
        return new MatchResult(order, ImmutableList.of());
    }

    /**
     * Creates a MatchResult with trades.
     */
    public static MatchResult withTrades(Order order, ImmutableList<Trade> trades) {
        return new MatchResult(order, trades);
    }

    /**
     * Checks if any trades were executed.
     */
    public boolean hasMatches() {
        return !trades.isEmpty();
    }

    /**
     * Gets the number of trades executed.
     */
    public int getTradeCount() {
        return trades.size();
    }
}
