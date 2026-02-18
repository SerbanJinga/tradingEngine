package com.trading.domain.event;

import com.trading.domain.model.AggregateId;

import java.time.Instant;

/**
 * Base interface for all domain events in the event-sourced system.
 * All domain events must implement this interface.
 */
public interface DomainEvent {

    /**
     * Gets the unique identifier for this event.
     */
    String getEventId();

    /**
     * Gets the aggregate ID this event belongs to.
     */
    AggregateId getAggregateId();

    /**
     * Gets the timestamp when this event occurred.
     */
    Instant getOccurredAt();

    /**
     * Gets the type name of this event.
     */
    String getEventType();
}
