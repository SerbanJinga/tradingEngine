package com.trading.domain.command;

import com.trading.domain.model.OrderId;
import com.trading.domain.model.Symbol;
import com.trading.domain.model.UserId;

/**
 * Command to cancel an existing order.
 * Immutable command object represented as a record.
 */
public record CancelOrderCommand(
    OrderId orderId,
    UserId userId,
    Symbol symbol
) {

    public CancelOrderCommand {
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
    }
}
