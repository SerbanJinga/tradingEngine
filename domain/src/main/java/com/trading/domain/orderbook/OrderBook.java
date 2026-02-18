package com.trading.domain.orderbook;

import com.google.common.collect.ImmutableList;
import com.trading.domain.model.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order book for a single trading symbol.
 * <p>
 * Maintains price-time priority for bid/ask orders and executes trades when prices cross.
 * <p>
 * <b>Thread Safety:</b> NOT thread-safe. In Iteration 2+, concurrency will be handled
 * at the aggregate level with optimistic locking.
 * <p>
 * <b>Data Structures:</b>
 * <ul>
 *   <li>TreeMap: Sorted price levels (O(log n) insertion/deletion)</li>
 *   <li>LinkedList: FIFO queue at each price level</li>
 *   <li>HashMap: O(1) order lookup by ID</li>
 * </ul>
 * <p>
 * <b>Matching Rules:</b>
 * <ol>
 *   <li>Price Priority: Best price matched first</li>
 *   <li>Time Priority: Within same price, FIFO</li>
 *   <li>Execution Price: Maker's price always wins</li>
 * </ol>
 */
public class OrderBook {
    private final Symbol symbol;

    // Bids: Higher price = better → descending order
    private final TreeMap<BigDecimal, LinkedList<Order>> bids;

    // Asks: Lower price = better → ascending order
    private final TreeMap<BigDecimal, LinkedList<Order>> asks;

    // Fast O(1) order lookup
    private final Map<OrderId, Order> orderIndex;

    /**
     * Creates a new order book for the specified symbol.
     *
     * @param symbol trading pair (e.g., BTC-USD)
     */
    public OrderBook(Symbol symbol) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Comparator.reverseOrder()); // Descending
        this.asks = new TreeMap<>(); // Ascending
        this.orderIndex = new HashMap<>();
    }

    /**
     * Adds an order and attempts to match it against the opposite side.
     * <p>
     * Process:
     * <ol>
     *   <li>Validate order symbol matches this book</li>
     *   <li>Attempt to match against opposite side</li>
     *   <li>If not fully filled, add remainder to book</li>
     *   <li>Return result with trades executed</li>
     * </ol>
     *
     * @param order order to add
     * @return match result containing updated order and trades
     * @throws IllegalArgumentException if order symbol doesn't match book symbol
     */
    public MatchResult addOrder(Order order) {
        if (!order.symbol().equals(this.symbol)) {
            throw new IllegalArgumentException(
                String.format("Order symbol %s does not match order book symbol %s",
                    order.symbol().value(), this.symbol.value())
            );
        }

        // Attempt to match
        MatchResult matchResult = match(order);

        // If order not fully filled, add remainder to book
        Order resultOrder = matchResult.order();
        if (resultOrder.isOpen()) {
            addToBook(resultOrder);
        }

        return matchResult;
    }

    /**
     * Cancels an order by ID.
     *
     * @param orderId order ID to cancel
     * @return true if order was found and cancelled, false otherwise
     */
    public boolean cancelOrder(OrderId orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null) {
            return false;
        }

        // Determine which side to remove from
        TreeMap<BigDecimal, LinkedList<Order>> side =
            order.side() == Side.BUY ? bids : asks;

        // Remove from price level
        LinkedList<Order> ordersAtPrice = side.get(order.price());
        if (ordersAtPrice != null) {
            ordersAtPrice.remove(order);

            // Clean up empty price level
            if (ordersAtPrice.isEmpty()) {
                side.remove(order.price());
            }
        }

        // Remove from index
        orderIndex.remove(orderId);

        return true;
    }

    /**
     * Matches an incoming order against the opposite side of the book.
     * <p>
     * Matching algorithm:
     * <ol>
     *   <li>Determine opposite side (BUY → ASKs, SELL → BIDs)</li>
     *   <li>While order has remaining quantity AND prices cross:</li>
     *   <li>  - Take best price level from opposite side</li>
     *   <li>  - Match against orders in FIFO order</li>
     *   <li>  - Create Trade for each match</li>
     *   <li>  - Update filled quantities</li>
     *   <li>  - Remove fully filled orders</li>
     *   <li>Return match result with trades and updated order</li>
     * </ol>
     *
     * @param incomingOrder order to match
     * @return match result with trades and potentially updated order
     */
    private MatchResult match(Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();
        Order currentOrder = incomingOrder;

        // Determine opposite side
        TreeMap<BigDecimal, LinkedList<Order>> oppositeSide =
            incomingOrder.side() == Side.BUY ? asks : bids;

        // Keep matching while order has remaining quantity
        while (currentOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // Get best price on opposite side
            Map.Entry<BigDecimal, LinkedList<Order>> bestPriceLevel =
                oppositeSide.firstEntry();

            if (bestPriceLevel == null) {
                // No more orders to match against
                break;
            }

            BigDecimal bestPrice = bestPriceLevel.getKey();

            // Check if prices cross
            if (!pricesCross(currentOrder.side(), currentOrder.price(), bestPrice)) {
                // No match possible
                break;
            }

            // Match against orders at this price level (FIFO)
            LinkedList<Order> ordersAtPrice = bestPriceLevel.getValue();
            Iterator<Order> iterator = ordersAtPrice.iterator();

            while (iterator.hasNext() &&
                   currentOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                Order makerOrder = iterator.next();

                // Calculate match quantity (min of both remaining quantities)
                BigDecimal matchQuantity = currentOrder.getRemainingQuantity()
                    .min(makerOrder.getRemainingQuantity());

                // Create trade (at maker's price)
                Trade trade = Trade.create(
                    symbol,
                    makerOrder.orderId(),
                    currentOrder.orderId(),
                    makerOrder.userId(),
                    currentOrder.userId(),
                    makerOrder.side(),
                    makerOrder.price(),  // Maker price wins
                    matchQuantity
                );

                trades.add(trade);

                // Update filled quantities
                BigDecimal newMakerFilled = makerOrder.filledQuantity().add(matchQuantity);
                BigDecimal newTakerFilled = currentOrder.filledQuantity().add(matchQuantity);

                Order updatedMaker = makerOrder.withFilledQuantity(newMakerFilled);
                currentOrder = currentOrder.withFilledQuantity(newTakerFilled);

                // Update or remove maker order
                if (updatedMaker.isFilled()) {
                    iterator.remove();
                    orderIndex.remove(updatedMaker.orderId());
                } else {
                    // Update order in place (LinkedList allows modification during iteration)
                    iterator.remove();
                    ordersAtPrice.add(updatedMaker);
                    orderIndex.put(updatedMaker.orderId(), updatedMaker);
                }
            }

            // Clean up empty price level
            if (ordersAtPrice.isEmpty()) {
                oppositeSide.remove(bestPrice);
            }
        }

        return MatchResult.withTrades(currentOrder, ImmutableList.copyOf(trades));
    }

    /**
     * Checks if prices cross (match is possible).
     * <p>
     * For BUY order: can match if BUY price >= ASK price
     * For SELL order: can match if SELL price <= BID price
     *
     * @param incomingSide side of incoming order
     * @param incomingPrice price of incoming order
     * @param bookPrice price in the book
     * @return true if prices cross and match is possible
     */
    private boolean pricesCross(Side incomingSide, BigDecimal incomingPrice, BigDecimal bookPrice) {
        if (incomingSide == Side.BUY) {
            // BUY at X matches SELL at Y if X >= Y
            return incomingPrice.compareTo(bookPrice) >= 0;
        } else {
            // SELL at X matches BUY at Y if X <= Y
            return incomingPrice.compareTo(bookPrice) <= 0;
        }
    }

    /**
     * Adds order to the book after matching attempt.
     * <p>
     * Only called if order has remaining quantity.
     * Maintains price-time priority by adding to end of queue at price level.
     *
     * @param order order to add
     */
    private void addToBook(Order order) {
        TreeMap<BigDecimal, LinkedList<Order>> side =
            order.side() == Side.BUY ? bids : asks;

        // Get or create price level
        LinkedList<Order> ordersAtPrice = side.computeIfAbsent(
            order.price(),
            k -> new LinkedList<>()
        );

        // Add to end of queue (FIFO)
        ordersAtPrice.add(order);

        // Add to index
        orderIndex.put(order.orderId(), order);
    }

    // ============ Query Methods ============
    // All query methods return immutable copies to prevent external modification

    /**
     * Gets all bid orders sorted by price descending, then time.
     * <p>
     * Returns immutable copy - modifications will not affect the book.
     *
     * @return immutable list of bid orders
     */
    public ImmutableList<Order> getBids() {
        return ImmutableList.copyOf(
            bids.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );
    }

    /**
     * Gets all ask orders sorted by price ascending, then time.
     * <p>
     * Returns immutable copy - modifications will not affect the book.
     *
     * @return immutable list of ask orders
     */
    public ImmutableList<Order> getAsks() {
        return ImmutableList.copyOf(
            asks.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );
    }

    /**
     * Gets best (highest) bid price.
     *
     * @return best bid price, or empty if no bids
     */
    public Optional<BigDecimal> getBestBidPrice() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
    }

    /**
     * Gets best (lowest) ask price.
     *
     * @return best ask price, or empty if no asks
     */
    public Optional<BigDecimal> getBestAskPrice() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
    }

    /**
     * Gets order by ID.
     *
     * @param orderId order ID to lookup
     * @return order if found, empty otherwise
     */
    public Optional<Order> getOrder(OrderId orderId) {
        return Optional.ofNullable(orderIndex.get(orderId));
    }

    /**
     * Gets the symbol for this order book.
     *
     * @return trading symbol
     */
    public Symbol getSymbol() {
        return symbol;
    }

    /**
     * Checks if order book is empty (no orders on either side).
     *
     * @return true if both bids and asks are empty
     */
    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    /**
     * Gets total number of orders in the book.
     *
     * @return total count of bid and ask orders
     */
    public int getOrderCount() {
        return orderIndex.size();
    }
}
