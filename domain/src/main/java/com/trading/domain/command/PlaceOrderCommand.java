package com.trading.domain.command;

import com.trading.domain.model.*;

import java.math.BigDecimal;

/**
 * Command to place a new order in the order book.
 * Immutable command object represented as a record.
 */
public record PlaceOrderCommand(
    OrderId orderId,
    UserId userId,
    Symbol symbol,
    Side side,
    OrderType type,
    BigDecimal price,
    BigDecimal quantity
) {

    public PlaceOrderCommand {
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("Side cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("OrderType cannot be null");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
