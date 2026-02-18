package com.trading.domain.aggregate;

import com.google.common.collect.ImmutableList;
import com.trading.domain.command.CancelOrderCommand;
import com.trading.domain.command.PlaceOrderCommand;
import com.trading.domain.event.*;
import com.trading.domain.model.*;
import com.trading.domain.orderbook.OrderBook;

import java.util.ArrayList;
import java.util.List;

/**
 * Event-sourced aggregate root for an OrderBook.
 * <p>
 * Manages the lifecycle of an order book for a specific symbol,
 * handling commands and emitting domain events.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li>Handle PlaceOrder and CancelOrder commands</li>
 *   <li>Emit domain events for all state changes</li>
 *   <li>Maintain current state via OrderBook</li>
 *   <li>Track uncommitted events for event store persistence</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> NOT thread-safe. Concurrency will be handled
 * at the application layer with optimistic locking in Iteration 2+.
 */
public class OrderBookAggregate {
    private final AggregateId aggregateId;
    private final Symbol symbol;
    private final OrderBook orderBook;
    private final List<DomainEvent> uncommittedEvents;
    private long version;

    /**
     * Creates a new OrderBookAggregate for the specified symbol.
     *
     * @param symbol trading pair (e.g., BTC-USD)
     */
    public OrderBookAggregate(Symbol symbol) {
        this.symbol = symbol;
        this.aggregateId = AggregateId.forOrderBook(symbol);
        this.orderBook = new OrderBook(symbol);
        this.uncommittedEvents = new ArrayList<>();
        this.version = 0;
    }

    /**
     * Handles a PlaceOrderCommand, matches the order, and emits events.
     * <p>
     * Process:
     * <ol>
     *   <li>Validate command</li>
     *   <li>Create Order from command</li>
     *   <li>Match order against book</li>
     *   <li>Emit OrderPlacedEvent</li>
     *   <li>Emit TradeExecutedEvent for each trade</li>
     *   <li>Emit OrderFilled/OrderPartiallyFilled if applicable</li>
     * </ol>
     *
     * @param command place order command
     * @return list of domain events emitted
     */
    public ImmutableList<DomainEvent> handle(PlaceOrderCommand command) {
        // Validate symbol matches this aggregate
        if (!command.symbol().equals(this.symbol)) {
            throw new IllegalArgumentException(
                String.format("Command symbol %s does not match aggregate symbol %s",
                    command.symbol().value(), this.symbol.value())
            );
        }

        List<DomainEvent> events = new ArrayList<>();

        // Create order from command
        Order order = Order.create(
            command.orderId(),
            command.userId(),
            command.symbol(),
            command.side(),
            command.type(),
            command.price(),
            command.quantity()
        );

        // Emit OrderPlacedEvent
        OrderPlacedEvent orderPlaced = OrderPlacedEvent.create(
            aggregateId,
            command.orderId(),
            command.userId(),
            command.symbol(),
            command.side(),
            command.type(),
            command.price(),
            command.quantity()
        );
        events.add(orderPlaced);

        // Match order against book
        MatchResult matchResult = orderBook.addOrder(order);

        // Emit TradeExecutedEvent for each trade
        for (Trade trade : matchResult.trades()) {
            TradeExecutedEvent tradeExecuted = TradeExecutedEvent.fromTrade(aggregateId, trade);
            events.add(tradeExecuted);
        }

        // Emit OrderFilled or OrderPartiallyFilled if matched
        Order resultOrder = matchResult.order();
        if (resultOrder.isFilled()) {
            OrderFilledEvent orderFilled = OrderFilledEvent.create(
                aggregateId,
                resultOrder.orderId(),
                resultOrder.symbol(),
                resultOrder.filledQuantity()
            );
            events.add(orderFilled);
        } else if (resultOrder.filledQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
            OrderPartiallyFilledEvent orderPartiallyFilled = OrderPartiallyFilledEvent.create(
                aggregateId,
                resultOrder.orderId(),
                resultOrder.symbol(),
                resultOrder.filledQuantity(),
                resultOrder.getRemainingQuantity()
            );
            events.add(orderPartiallyFilled);
        }

        // Track uncommitted events
        uncommittedEvents.addAll(events);
        version++;

        return ImmutableList.copyOf(events);
    }

    /**
     * Handles a CancelOrderCommand and emits events.
     * <p>
     * Process:
     * <ol>
     *   <li>Validate command</li>
     *   <li>Cancel order in book</li>
     *   <li>Emit OrderCancelledEvent if successful</li>
     * </ol>
     *
     * @param command cancel order command
     * @return list of domain events emitted
     * @throws IllegalArgumentException if order not found or symbol mismatch
     */
    public ImmutableList<DomainEvent> handle(CancelOrderCommand command) {
        // Validate symbol matches this aggregate
        if (!command.symbol().equals(this.symbol)) {
            throw new IllegalArgumentException(
                String.format("Command symbol %s does not match aggregate symbol %s",
                    command.symbol().value(), this.symbol.value())
            );
        }

        List<DomainEvent> events = new ArrayList<>();

        // Cancel order in book
        boolean cancelled = orderBook.cancelOrder(command.orderId());
        if (!cancelled) {
            throw new IllegalArgumentException(
                "Order not found: " + command.orderId().value()
            );
        }

        // Emit OrderCancelledEvent
        OrderCancelledEvent orderCancelled = OrderCancelledEvent.create(
            aggregateId,
            command.orderId(),
            command.symbol()
        );
        events.add(orderCancelled);

        // Track uncommitted events
        uncommittedEvents.addAll(events);
        version++;

        return ImmutableList.copyOf(events);
    }

    /**
     * Gets all uncommitted events since last commit.
     *
     * @return immutable list of uncommitted events
     */
    public ImmutableList<DomainEvent> getUncommittedEvents() {
        return ImmutableList.copyOf(uncommittedEvents);
    }

    /**
     * Marks all uncommitted events as committed.
     * Call this after successfully persisting events to the event store.
     */
    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    /**
     * Gets the aggregate ID.
     *
     * @return aggregate ID
     */
    public AggregateId getAggregateId() {
        return aggregateId;
    }

    /**
     * Gets the trading symbol for this order book.
     *
     * @return trading symbol
     */
    public Symbol getSymbol() {
        return symbol;
    }

    /**
     * Gets the current version of this aggregate.
     * Incremented with each command handled.
     *
     * @return current version
     */
    public long getVersion() {
        return version;
    }

    /**
     * Gets the current state of the order book.
     *
     * @return order book
     */
    public OrderBook getOrderBook() {
        return orderBook;
    }
}
