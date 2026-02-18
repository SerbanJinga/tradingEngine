package com.trading.domain.event;

import com.trading.domain.model.AggregateId;
import com.trading.domain.model.OrderId;
import com.trading.domain.model.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an order is partially filled.
 * Immutable event object represented as a record.
 */
public record OrderPartiallyFilledEvent(
    String eventId,
    AggregateId aggregateId,
    Instant occurredAt,
    OrderId orderId,
    Symbol symbol,
    BigDecimal filledQuantity,
    BigDecimal remainingQuantity
) implements DomainEvent {

    public OrderPartiallyFilledEvent {
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
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
        if (filledQuantity == null || filledQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("FilledQuantity must be positive");
        }
        if (remainingQuantity == null || remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("RemainingQuantity must be positive");
        }
    }

    public static OrderPartiallyFilledEvent create(
        AggregateId aggregateId,
        OrderId orderId,
        Symbol symbol,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity
    ) {
        return new OrderPartiallyFilledEvent(
            UUID.randomUUID().toString(),
            aggregateId,
            Instant.now(),
            orderId,
            symbol,
            filledQuantity,
            remainingQuantity
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
        return "OrderPartiallyFilled";
    }
}
