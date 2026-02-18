package com.trading.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an executed trade between two orders.
 * Immutable domain entity represented as a record.
 */
public record Trade(
    String tradeId,
    Symbol symbol,
    OrderId makerOrderId,
    OrderId takerOrderId,
    UserId makerId,
    UserId takerId,
    Side makerSide,
    BigDecimal price,
    BigDecimal quantity,
    Instant executedAt
) {

    public Trade {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("TradeId cannot be null or blank");
        }
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
        if (makerOrderId == null) {
            throw new IllegalArgumentException("MakerOrderId cannot be null");
        }
        if (takerOrderId == null) {
            throw new IllegalArgumentException("TakerOrderId cannot be null");
        }
        if (makerId == null) {
            throw new IllegalArgumentException("MakerId cannot be null");
        }
        if (takerId == null) {
            throw new IllegalArgumentException("TakerId cannot be null");
        }
        if (makerSide == null) {
            throw new IllegalArgumentException("MakerSide cannot be null");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (executedAt == null) {
            throw new IllegalArgumentException("ExecutedAt cannot be null");
        }
    }

    /**
     * Creates a new trade with generated ID and current timestamp.
     */
    public static Trade create(
        Symbol symbol,
        OrderId makerOrderId,
        OrderId takerOrderId,
        UserId makerId,
        UserId takerId,
        Side makerSide,
        BigDecimal price,
        BigDecimal quantity
    ) {
        return new Trade(
            UUID.randomUUID().toString(),
            symbol,
            makerOrderId,
            takerOrderId,
            makerId,
            takerId,
            makerSide,
            price,
            quantity,
            Instant.now()
        );
    }

    /**
     * Gets the taker's side (opposite of maker's side).
     */
    public Side getTakerSide() {
        return makerSide == Side.BUY ? Side.SELL : Side.BUY;
    }

    /**
     * Gets the buyer's user ID.
     */
    public UserId getBuyerId() {
        return makerSide == Side.BUY ? makerId : takerId;
    }

    /**
     * Gets the seller's user ID.
     */
    public UserId getSellerId() {
        return makerSide == Side.SELL ? makerId : takerId;
    }
}
