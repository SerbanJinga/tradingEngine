package com.trading.domain.event;

import com.trading.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a trade is executed between two orders.
 * Immutable event object represented as a record.
 */
public record TradeExecutedEvent(
    String eventId,
    AggregateId aggregateId,
    Instant occurredAt,
    String tradeId,
    Symbol symbol,
    OrderId makerOrderId,
    OrderId takerOrderId,
    UserId makerId,
    UserId takerId,
    Side makerSide,
    BigDecimal price,
    BigDecimal quantity
) implements DomainEvent {

    public TradeExecutedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("EventId cannot be null or blank");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("AggregateId cannot be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("OccurredAt cannot be null");
        }
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
    }

    public static TradeExecutedEvent fromTrade(AggregateId aggregateId, Trade trade) {
        return new TradeExecutedEvent(
            UUID.randomUUID().toString(),
            aggregateId,
            Instant.now(),
            trade.tradeId(),
            trade.symbol(),
            trade.makerOrderId(),
            trade.takerOrderId(),
            trade.makerId(),
            trade.takerId(),
            trade.makerSide(),
            trade.price(),
            trade.quantity()
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
        return "TradeExecuted";
    }
}
