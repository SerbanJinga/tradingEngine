package com.trading.domain.event;

import com.trading.domain.model.AggregateId;
import com.trading.domain.model.OrderId;
import com.trading.domain.model.Symbol;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an order is cancelled.
 * Immutable event object represented as a record.
 */
public record OrderCancelledEvent(
    String eventId,
    AggregateId aggregateId,
    Instant occurredAt,
    OrderId orderId,
    Symbol symbol
) implements DomainEvent {

    public OrderCancelledEvent {
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
    }

    public static OrderCancelledEvent create(
        AggregateId aggregateId,
        OrderId orderId,
        Symbol symbol
    ) {
        return new OrderCancelledEvent(
            UUID.randomUUID().toString(),
            aggregateId,
            Instant.now(),
            orderId,
            symbol
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
        return "OrderCancelled";
    }
}
