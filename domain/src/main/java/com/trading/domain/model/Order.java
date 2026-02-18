package com.trading.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a trading order in the system.
 * Immutable domain entity represented as a record.
 */
public record Order(
    OrderId orderId,
    UserId userId,
    Symbol symbol,
    Side side,
    OrderType type,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal filledQuantity,
    OrderStatus status,
    Instant createdAt
) {

    public Order {
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
        if (filledQuantity == null || filledQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("FilledQuantity cannot be negative");
        }
        if (filledQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("FilledQuantity cannot exceed quantity");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
    }

    /**
     * Creates a new order with default values for filled quantity and creation time.
     */
    public static Order create(
        OrderId orderId,
        UserId userId,
        Symbol symbol,
        Side side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity
    ) {
        return new Order(
            orderId,
            userId,
            symbol,
            side,
            type,
            price,
            quantity,
            BigDecimal.ZERO,
            OrderStatus.OPEN,
            Instant.now()
        );
    }

    /**
     * Gets the remaining (unfilled) quantity.
     */
    public BigDecimal getRemainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    /**
     * Checks if the order is completely filled.
     */
    public boolean isFilled() {
        return filledQuantity.compareTo(quantity) == 0;
    }

    /**
     * Checks if the order is open (not filled and not cancelled).
     */
    public boolean isOpen() {
        return status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED;
    }

    /**
     * Creates a copy with updated filled quantity.
     */
    public Order withFilledQuantity(BigDecimal newFilled) {
        OrderStatus newStatus;
        if (newFilled.compareTo(quantity) == 0) {
            newStatus = OrderStatus.FILLED;
        } else if (newFilled.compareTo(BigDecimal.ZERO) > 0) {
            newStatus = OrderStatus.PARTIALLY_FILLED;
        } else {
            newStatus = OrderStatus.OPEN;
        }

        return new Order(
            orderId,
            userId,
            symbol,
            side,
            type,
            price,
            quantity,
            newFilled,
            newStatus,
            createdAt
        );
    }

    /**
     * Creates a copy with cancelled status.
     */
    public Order withCancelledStatus() {
        return new Order(
            orderId,
            userId,
            symbol,
            side,
            type,
            price,
            quantity,
            filledQuantity,
            OrderStatus.CANCELLED,
            createdAt
        );
    }
}
