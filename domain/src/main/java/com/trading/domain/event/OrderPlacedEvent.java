package com.trading.domain.event;

import com.trading.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a new order is placed in the order book.
 * Immutable event object represented as a record.
 */
public record OrderPlacedEvent(
    String eventId,
    AggregateId aggregateId,
    Instant occurredAt,
    OrderId orderId,
    UserId userId,
    Symbol symbol,
    Side side,
    OrderType type,
    BigDecimal price,
    BigDecimal quantity
) implements DomainEvent {

    public OrderPlacedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("EventId cannot be null or blank");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("AggregateId cannot be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("OccurredAt cannot be null");
        }
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

    public static OrderPlacedEvent create(
        AggregateId aggregateId,
        OrderId orderId,
        UserId userId,
        Symbol symbol,
        Side side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity
    ) {
        return new OrderPlacedEvent(
            UUID.randomUUID().toString(),
            aggregateId,
            Instant.now(),
            orderId,
            userId,
            symbol,
            side,
            type,
            price,
            quantity
        );
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public AggregateId getAggregateId() {
        return aggregateId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getEventType() {
        return "OrderPlaced";
    }
}
