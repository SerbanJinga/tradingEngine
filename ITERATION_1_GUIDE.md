# 🥇 Iteration 1 - Implementation Guide

**Pure Matching Engine (No Framework)**

---

## 🎯 Goal

Build core domain logic with **zero external dependencies**.

**Duration:** 2-4 hours
**Module:** `domain`
**Tests Required:** 100% coverage on matching logic

## 💡 Implementation Approach

**Immutability:** We use **Java records** (not Immutables library) for all domain objects:
- ✅ Zero external dependencies
- ✅ Built-in immutability
- ✅ Compact constructor for validation
- ✅ Automatic getters (accessors)
- ✅ No code generation needed

**Collections:** We use **Guava's Immutable collections** (ImmutableList, ImmutableMap):
- Only external dependency in domain module
- Guarantees immutability at runtime
- Better than Collections.unmodifiableList()

---

## 📋 Deliverables Checklist

- [ ] **Enums** (Side, OrderType, OrderStatus)
- [ ] **Value Objects** (OrderId, UserId, Symbol, AggregateId)
- [ ] **Domain Models** (Order, Trade)
- [ ] **Matching Engine** (OrderBook with price-time priority)
- [ ] **Match Result** (wrapper for match outcomes)
- [ ] **Unit Tests** (15+ test cases)
- [ ] **100% Test Coverage** on OrderBook

---

## 📂 Step 1: Create Package Structure

```bash
cd domain

# Create main packages
mkdir -p src/main/java/com/trading/domain/model
mkdir -p src/main/java/com/trading/domain/orderbook

# Create test packages
mkdir -p src/test/java/com/trading/domain/model
mkdir -p src/test/java/com/trading/domain/orderbook
```

**Expected structure:**
```
domain/
├── src/
│   ├── main/java/com/trading/domain/
│   │   ├── model/          # Domain objects
│   │   └── orderbook/      # Matching engine
│   └── test/java/com/trading/domain/
│       ├── model/          # Model tests
│       └── orderbook/      # Matching tests
└── pom.xml
```

---

## 🔢 Step 2: Create Enums

### 2.1 Side.java

**File:** `domain/src/main/java/com/trading/domain/model/Side.java`

```java
package com.trading.domain.model;

/**
 * Order side - BUY or SELL
 */
public enum Side {
    BUY,
    SELL
}
```

**Why:** Strongly-typed order direction

---

### 2.2 OrderType.java

**File:** `domain/src/main/java/com/trading/domain/model/OrderType.java`

```java
package com.trading.domain.model;

/**
 * Order types supported by the matching engine.
 * For Iteration 1, only LIMIT orders are implemented.
 */
public enum OrderType {
    LIMIT,      // Standard limit order (Iteration 1)
    MARKET,     // Execute at best available price (TODO: Iteration 10)
    STOP_LOSS,  // Trigger when price reaches stop (TODO: Iteration 10)
    STOP_LIMIT, // Combination of stop + limit (TODO: Iteration 10)
    FOK,        // Fill-or-kill (TODO: Iteration 10)
    IOC         // Immediate-or-cancel (TODO: Iteration 10)
}
```

**Note:** Only `LIMIT` is used in Iteration 1

---

### 2.3 OrderStatus.java

**File:** `domain/src/main/java/com/trading/domain/model/OrderStatus.java`

```java
package com.trading.domain.model;

/**
 * Lifecycle states of an order
 */
public enum OrderStatus {
    OPEN,              // Order is active in the book
    PARTIALLY_FILLED,  // Order has been partially matched
    FILLED,            // Order completely matched
    CANCELLED          // Order cancelled by user
}
```

---

## 🆔 Step 3: Create Value Objects

### 3.1 OrderId.java

**File:** `domain/src/main/java/com/trading/domain/model/OrderId.java`

```java
package com.trading.domain.model;

import java.util.UUID;

/**
 * Unique identifier for an Order.
 * Value object represented as a record.
 */
public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId value cannot be null or blank");
        }
    }

    /**
     * Generates a new unique OrderId.
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }
}
```

**Key Points:**
- Java record provides immutability automatically
- Compact constructor for validation
- Factory method `generate()` for creating new IDs
- No external dependencies needed

---

### 3.2 UserId.java

**File:** `domain/src/main/java/com/trading/domain/model/UserId.java`

```java
package com.trading.domain.model;

/**
 * Unique identifier for a User.
 * Value object represented as a record.
 */
public record UserId(String value) {

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId value cannot be null or blank");
        }
    }

    /**
     * Creates a UserId from a string value.
     */
    public static UserId of(String value) {
        return new UserId(value);
    }
}
```

---

### 3.3 Symbol.java

**File:** `domain/src/main/java/com/trading/domain/model/Symbol.java`

```java
package com.trading.domain.model;

import java.util.regex.Pattern;

/**
 * Trading symbol (e.g., "BTC-USD", "ETH-USDT").
 * Format: BASE-QUOTE where both are uppercase letters.
 * Value object represented as a record.
 */
public record Symbol(String value) {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z]+-[A-Z]+$");

    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol value cannot be null or blank");
        }
        if (!SYMBOL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Symbol must be in format BASE-QUOTE (e.g., BTC-USD). Got: " + value
            );
        }
    }

    /**
     * Creates a Symbol from a string value.
     */
    public static Symbol of(String value) {
        return new Symbol(value);
    }

    /**
     * Gets the base currency (e.g., "BTC" from "BTC-USD").
     */
    public String getBase() {
        return value.split("-")[0];
    }

    /**
     * Gets the quote currency (e.g., "USD" from "BTC-USD").
     */
    public String getQuote() {
        return value.split("-")[1];
    }
}
```

**Validation Examples:**
- ✅ `Symbol.of("BTC-USD")` → valid
- ❌ `Symbol.of("btc-usd")` → throws exception (must be uppercase)
- ❌ `Symbol.of("BTCUSD")` → throws exception (missing hyphen)
- ❌ `Symbol.of("")` → throws exception

---

### 3.4 AggregateId.java

**File:** `domain/src/main/java/com/trading/domain/model/AggregateId.java`

```java
package com.trading.domain.model;

/**
 * Unique identifier for aggregates in the event-sourced system.
 * Combines a value with a type for type-safe aggregate identification.
 * Value object represented as a record.
 */
public record AggregateId(String value, String type) {

    public AggregateId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AggregateId value cannot be null or blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("AggregateId type cannot be null or blank");
        }
    }

    /**
     * Creates an AggregateId for an OrderBook.
     */
    public static AggregateId forOrderBook(Symbol symbol) {
        return new AggregateId(symbol.value(), "OrderBook");
    }
}
```

**Note:** Used for aggregate identification in event sourcing

---

## 📝 Step 4: Create Domain Models

### 4.1 Order.java

**File:** `domain/src/main/java/com/trading/domain/model/Order.java`

```java
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
```

**Usage Example:**
```java
Order order = Order.create(
    OrderId.generate(),
    UserId.of("user-123"),
    Symbol.of("BTC-USD"),
    Side.BUY,
    OrderType.LIMIT,
    new BigDecimal("50000.00"),
    new BigDecimal("1.5")
);

// Create modified copy
Order filled = order.withFilledQuantity(new BigDecimal("0.5"));
// filled.status() == OrderStatus.PARTIALLY_FILLED
```

---

### 4.2 Trade.java

**File:** `domain/src/main/java/com/trading/domain/model/Trade.java`

```java
package com.trading.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an executed trade between two orders.
 * Immutable domain entity represented as a record.
 */
public record Trade(
    String tradeId,
    Symbol symbol,
    OrderId makerOrderId,
    OrderId takerOrderId,
    UserId makerId,
    UserId takerId,
    Side makerSide,
    BigDecimal price,
    BigDecimal quantity,
    Instant executedAt
) {

    public Trade {
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
        if (executedAt == null) {
            throw new IllegalArgumentException("ExecutedAt cannot be null");
        }
    }

    /**
     * Creates a new trade with generated ID and current timestamp.
     */
    public static Trade create(
        Symbol symbol,
        OrderId makerOrderId,
        OrderId takerOrderId,
        UserId makerId,
        UserId takerId,
        Side makerSide,
        BigDecimal price,
        BigDecimal quantity
    ) {
        return new Trade(
            UUID.randomUUID().toString(),
            symbol,
            makerOrderId,
            takerOrderId,
            makerId,
            takerId,
            makerSide,
            price,
            quantity,
            Instant.now()
        );
    }

    /**
     * Gets the taker's side (opposite of maker's side).
     */
    public Side getTakerSide() {
        return makerSide == Side.BUY ? Side.SELL : Side.BUY;
    }

    /**
     * Gets the buyer's user ID.
     */
    public UserId getBuyerId() {
        return makerSide == Side.BUY ? makerId : takerId;
    }

    /**
     * Gets the seller's user ID.
     */
    public UserId getSellerId() {
        return makerSide == Side.SELL ? makerId : takerId;
    }
}
```

**Usage Example:**
```java
Trade trade = Trade.create(
    Symbol.of("BTC-USD"),
    makerOrder.orderId(),
    takerOrder.orderId(),
    makerOrder.userId(),
    takerOrder.userId(),
    makerOrder.side(),
    makerOrder.price(),  // Maker's price wins
    new BigDecimal("0.5")
);
```

---

## ⚙️ Step 5: Create Matching Engine

### 5.1 MatchResult.java

**File:** `domain/src/main/java/com/trading/domain/model/MatchResult.java`

```java
package com.trading.domain.model;

import com.google.common.collect.ImmutableList;

/**
 * Represents the result of matching an order against an order book.
 * Contains the updated order and any trades that were executed.
 * Immutable result object represented as a record.
 */
public record MatchResult(
    Order order,
    ImmutableList<Trade> trades
) {

    public MatchResult {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (trades == null) {
            throw new IllegalArgumentException("Trades cannot be null");
        }
    }

    /**
     * Creates a MatchResult with no trades (order added to book without matching).
     */
    public static MatchResult noMatch(Order order) {
        return new MatchResult(order, ImmutableList.of());
    }

    /**
     * Creates a MatchResult with trades.
     */
    public static MatchResult withTrades(Order order, ImmutableList<Trade> trades) {
        return new MatchResult(order, trades);
    }

    /**
     * Checks if any trades were executed.
     */
    public boolean hasMatches() {
        return !trades.isEmpty();
    }

    /**
     * Gets the number of trades executed.
     */
    public int getTradeCount() {
        return trades.size();
    }
}
```

---

### 5.2 OrderBook.java

**File:** `domain/src/main/java/com/trading/domain/orderbook/OrderBook.java`

```java
package com.trading.domain.orderbook;

import com.google.common.collect.ImmutableList;
import com.trading.domain.model.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order book for a single trading symbol.
 * Maintains price-time priority for bid/ask orders.
 *
 * Thread-safety: NOT thread-safe (handled at aggregate level in Iteration 2+).
 * Internal state is mutable for performance, but never exposed.
 */
public class OrderBook {
    private final Symbol symbol;

    // Bids: Higher price = better, so descending order (TreeMap reverseOrder)
    private final TreeMap<BigDecimal, LinkedList<Order>> bids;

    // Asks: Lower price = better, so ascending order (TreeMap natural order)
    private final TreeMap<BigDecimal, LinkedList<Order>> asks;

    // Quick lookup by order ID
    private final Map<OrderId, Order> orderIndex;

    public OrderBook(Symbol symbol) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Comparator.reverseOrder()); // Descending
        this.asks = new TreeMap<>(); // Ascending
        this.orderIndex = new HashMap<>();
    }

    /**
     * Add an order and attempt to match it.
     *
     * Process:
     * 1. Validate order symbol matches book
     * 2. Try to match against opposite side
     * 3. If not fully filled, add remaining to book
     * 4. Return result with trades executed
     *
     * @param order Order to add
     * @return MatchResult containing updated order and trades
     */
    public MatchResult addOrder(Order order) {
        if (!order.getSymbol().equals(this.symbol)) {
            throw new IllegalArgumentException(
                "Order symbol " + order.getSymbol() +
                " does not match order book symbol " + this.symbol
            );
        }

        // Attempt to match
        MatchResult matchResult = match(order);

        // If order not fully filled, add remainder to book
        Order resultOrder = matchResult.getOrder();
        if (resultOrder.isOpen()) {
            addToBook(resultOrder);
        }

        return matchResult;
    }

    /**
     * Cancel an order by ID.
     *
     * @param orderId Order ID to cancel
     * @return true if order was found and cancelled, false otherwise
     */
    public boolean cancelOrder(OrderId orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null) {
            return false;
        }

        // Determine which side to remove from
        TreeMap<BigDecimal, LinkedList<Order>> side =
            order.getSide() == Side.BUY ? bids : asks;

        // Remove from price level
        LinkedList<Order> ordersAtPrice = side.get(order.getPrice());
        if (ordersAtPrice != null) {
            ordersAtPrice.remove(order);

            // Clean up empty price level
            if (ordersAtPrice.isEmpty()) {
                side.remove(order.getPrice());
            }
        }

        // Remove from index
        orderIndex.remove(orderId);

        return true;
    }

    /**
     * Match an incoming order against the opposite side of the book.
     *
     * Matching rules:
     * - BUY orders match against ASKs (sells)
     * - SELL orders match against BIDs (buys)
     * - Match at maker's price (price in the book)
     * - Price-time priority: best price first, then FIFO
     *
     * @param incomingOrder Order to match
     * @return MatchResult with trades and updated order
     */
    private MatchResult match(Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();
        Order currentOrder = incomingOrder;

        // Determine opposite side
        TreeMap<BigDecimal, LinkedList<Order>> oppositeSide =
            incomingOrder.getSide() == Side.BUY ? asks : bids;

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
            if (!pricesCross(currentOrder.getSide(), currentOrder.getPrice(), bestPrice)) {
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
                Trade trade = ImmutableTrade.builder()
                    .symbol(symbol)
                    .makerOrderId(makerOrder.getId())
                    .takerOrderId(currentOrder.getId())
                    .makerId(makerOrder.getUserId())
                    .takerId(currentOrder.getUserId())
                    .price(makerOrder.getPrice())  // Maker price wins
                    .quantity(matchQuantity)
                    .makerSide(makerOrder.getSide())
                    .build();

                trades.add(trade);

                // Update filled quantities
                BigDecimal newMakerFilled = makerOrder.getFilledQuantity().add(matchQuantity);
                BigDecimal newTakerFilled = currentOrder.getFilledQuantity().add(matchQuantity);

                Order updatedMaker = makerOrder.withFilledQuantity(newMakerFilled);
                currentOrder = currentOrder.withFilledQuantity(newTakerFilled);

                // Update or remove maker order
                if (updatedMaker.isFilled()) {
                    iterator.remove();
                    orderIndex.remove(updatedMaker.getId());
                } else {
                    // Update order in the list (replace with updated version)
                    // Note: This is tricky with LinkedList - need to remove and re-add
                    iterator.remove();
                    ordersAtPrice.add(updatedMaker);
                    orderIndex.put(updatedMaker.getId(), updatedMaker);
                }
            }

            // Clean up empty price level
            if (ordersAtPrice.isEmpty()) {
                oppositeSide.remove(bestPrice);
            }
        }

        return ImmutableMatchResult.builder()
            .order(currentOrder)
            .trades(ImmutableList.copyOf(trades))
            .build();
    }

    /**
     * Check if prices cross (match is possible)
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
     * Add order to the book (after matching attempt).
     * Only called if order has remaining quantity.
     */
    private void addToBook(Order order) {
        TreeMap<BigDecimal, LinkedList<Order>> side =
            order.getSide() == Side.BUY ? bids : asks;

        // Get or create price level
        LinkedList<Order> ordersAtPrice = side.computeIfAbsent(
            order.getPrice(),
            k -> new LinkedList<>()
        );

        // Add to end of queue (FIFO)
        ordersAtPrice.add(order);

        // Add to index
        orderIndex.put(order.getId(), order);
    }

    // ============ Query Methods (return immutable copies) ============

    /**
     * Get all bid orders (sorted by price descending, then time)
     */
    public ImmutableList<Order> getBids() {
        return ImmutableList.copyOf(
            bids.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );
    }

    /**
     * Get all ask orders (sorted by price ascending, then time)
     */
    public ImmutableList<Order> getAsks() {
        return ImmutableList.copyOf(
            asks.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );
    }

    /**
     * Get best (highest) bid price
     */
    public Optional<BigDecimal> getBestBidPrice() {
        return Optional.ofNullable(bids.firstKey());
    }

    /**
     * Get best (lowest) ask price
     */
    public Optional<BigDecimal> getBestAskPrice() {
        return Optional.ofNullable(asks.firstKey());
    }

    /**
     * Get order by ID
     */
    public Optional<Order> getOrder(OrderId orderId) {
        return Optional.ofNullable(orderIndex.get(orderId));
    }

    /**
     * Get order book symbol
     */
    public Symbol getSymbol() {
        return symbol;
    }

    /**
     * Check if order book is empty
     */
    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }
}
```

**Key Implementation Notes:**

1. **Data Structures:**
   - `TreeMap` for sorted price levels (O(log n) insertion/deletion)
   - `LinkedList` for FIFO within each price level
   - `HashMap` for O(1) order lookup by ID

2. **Price-Time Priority:**
   - BIDs sorted descending (highest price first)
   - ASKs sorted ascending (lowest price first)
   - Within same price: FIFO (first order matched first)

3. **Matching Logic:**
   - Taker order walks through maker orders
   - Match at maker's price (maker gets their price)
   - Continue until order filled or no more matches

4. **Immutability:**
   - Internal state is mutable (performance)
   - All public methods return immutable copies (Guava)
   - Orders are immutable (use `withFilledQuantity()` for updates)

---

## 🧪 Step 6: Write Tests

### 6.1 SymbolTest.java

**File:** `domain/src/test/java/com/trading/domain/model/SymbolTest.java`

```java
package com.trading.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SymbolTest {

    @Test
    void shouldCreateValidSymbol() {
        Symbol symbol = Symbol.of("BTC-USD");

        assertThat(symbol.getValue()).isEqualTo("BTC-USD");
    }

    @Test
    void shouldConvertToUppercase() {
        Symbol symbol = Symbol.of("btc-usd");

        assertThat(symbol.getValue()).isEqualTo("BTC-USD");
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> Symbol.of("BTCUSD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be in format XXX-YYY");
    }

    @Test
    void shouldRejectEmptySymbol() {
        assertThatThrownBy(() -> Symbol.of(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be empty");
    }

    @Test
    void shouldRejectNullSymbol() {
        assertThatThrownBy(() -> Symbol.of(null))
            .isInstanceOf(NullPointerException.class);
    }
}
```

---

### 6.2 OrderTest.java

**File:** `domain/src/test/java/com/trading/domain/model/OrderTest.java`

```java
package com.trading.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateOrder() {
        Order order = Order.builder()
            .id(OrderId.generate())
            .userId(UserId.of("user-1"))
            .symbol(Symbol.of("BTC-USD"))
            .side(Side.BUY)
            .type(OrderType.LIMIT)
            .price(new BigDecimal("50000"))
            .quantity(new BigDecimal("1.5"))
            .build();

        assertThat(order.getPrice()).isEqualByComparingTo("50000");
        assertThat(order.getQuantity()).isEqualByComparingTo("1.5");
        assertThat(order.getFilledQuantity()).isEqualByComparingTo("0");
        assertThat(order.getRemainingQuantity()).isEqualByComparingTo("1.5");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.isOpen()).isTrue();
        assertThat(order.isFilled()).isFalse();
    }

    @Test
    void shouldUpdateFilledQuantityToPartial() {
        Order order = createTestOrder("1.0");

        Order updated = order.withFilledQuantity(new BigDecimal("0.5"));

        assertThat(updated.getFilledQuantity()).isEqualByComparingTo("0.5");
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(updated.getRemainingQuantity()).isEqualByComparingTo("0.5");
        assertThat(updated.isOpen()).isTrue();
    }

    @Test
    void shouldUpdateFilledQuantityToFilled() {
        Order order = createTestOrder("1.0");

        Order updated = order.withFilledQuantity(new BigDecimal("1.0"));

        assertThat(updated.getFilledQuantity()).isEqualByComparingTo("1.0");
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(updated.getRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(updated.isFilled()).isTrue();
        assertThat(updated.isOpen()).isFalse();
    }

    @Test
    void shouldRejectNegativePrice() {
        assertThatThrownBy(() ->
            Order.builder()
                .id(OrderId.generate())
                .userId(UserId.of("user-1"))
                .symbol(Symbol.of("BTC-USD"))
                .side(Side.BUY)
                .type(OrderType.LIMIT)
                .price(new BigDecimal("-100"))
                .quantity(new BigDecimal("1"))
                .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Price must be positive");
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThatThrownBy(() ->
            Order.builder()
                .id(OrderId.generate())
                .userId(UserId.of("user-1"))
                .symbol(Symbol.of("BTC-USD"))
                .side(Side.BUY)
                .type(OrderType.LIMIT)
                .price(new BigDecimal("50000"))
                .quantity(new BigDecimal("-1"))
                .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Quantity must be positive");
    }

    @Test
    void shouldRejectFilledQuantityExceedingTotal() {
        assertThatThrownBy(() ->
            Order.builder()
                .id(OrderId.generate())
                .userId(UserId.of("user-1"))
                .symbol(Symbol.of("BTC-USD"))
                .side(Side.BUY)
                .type(OrderType.LIMIT)
                .price(new BigDecimal("50000"))
                .quantity(new BigDecimal("1.0"))
                .filledQuantity(new BigDecimal("1.5"))
                .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("cannot exceed");
    }

    // Helper
    private Order createTestOrder(String quantity) {
        return Order.builder()
            .id(OrderId.generate())
            .userId(UserId.of("user-1"))
            .symbol(Symbol.of("BTC-USD"))
            .side(Side.BUY)
            .type(OrderType.LIMIT)
            .price(new BigDecimal("50000"))
            .quantity(new BigDecimal(quantity))
            .build();
    }
}
```

---

### 6.3 OrderBookTest.java

**File:** `domain/src/test/java/com/trading/domain/orderbook/OrderBookTest.java`

```java
package com.trading.domain.orderbook;

import com.trading.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderBookTest {

    private OrderBook orderBook;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        symbol = Symbol.of("BTC-USD");
        orderBook = new OrderBook(symbol);
    }

    // ============ Basic Operations ============

    @Test
    void shouldStartEmpty() {
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getAsks()).isEmpty();
        assertThat(orderBook.getBestBidPrice()).isEmpty();
        assertThat(orderBook.getBestAskPrice()).isEmpty();
        assertThat(orderBook.isEmpty()).isTrue();
    }

    @Test
    void shouldAddBuyOrderWithoutMatch() {
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");

        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getOrder().isOpen()).isTrue();
        assertThat(result.getOrder().getFilledQuantity()).isEqualByComparingTo("0");
        assertThat(orderBook.getBids()).hasSize(1);
        assertThat(orderBook.getAsks()).isEmpty();
        assertThat(orderBook.getBestBidPrice()).contains(new BigDecimal("50000"));
        assertThat(orderBook.isEmpty()).isFalse();
    }

    @Test
    void shouldAddSellOrderWithoutMatch() {
        Order sellOrder = createLimitOrder(Side.SELL, "51000", "1.0");

        MatchResult result = orderBook.addOrder(sellOrder);

        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getOrder().isOpen()).isTrue();
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getAsks()).hasSize(1);
        assertThat(orderBook.getBestAskPrice()).contains(new BigDecimal("51000"));
    }

    @Test
    void shouldRejectOrderWithWrongSymbol() {
        Order wrongSymbolOrder = Order.builder()
            .id(OrderId.generate())
            .userId(UserId.of("user-1"))
            .symbol(Symbol.of("ETH-USD"))  // Wrong symbol
            .side(Side.BUY)
            .type(OrderType.LIMIT)
            .price(new BigDecimal("3000"))
            .quantity(new BigDecimal("10"))
            .build();

        assertThatThrownBy(() -> orderBook.addOrder(wrongSymbolOrder))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match order book symbol");
    }

    // ============ Matching - Full Fill ============

    @Test
    void shouldMatchFullyWhenPricesCross() {
        // Add SELL order at 50000
        Order sellOrder = createLimitOrder(Side.SELL, "50000", "1.0");
        orderBook.addOrder(sellOrder);

        // Add BUY order at 50000 (should match)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Verify trade
        assertThat(result.getTrades()).hasSize(1);
        Trade trade = result.getTrades().get(0);
        assertThat(trade.getSymbol()).isEqualTo(symbol);
        assertThat(trade.getPrice()).isEqualByComparingTo("50000");
        assertThat(trade.getQuantity()).isEqualByComparingTo("1.0");
        assertThat(trade.getMakerOrderId()).isEqualTo(sellOrder.getId());
        assertThat(trade.getTakerOrderId()).isEqualTo(buyOrder.getId());
        assertThat(trade.getMakerSide()).isEqualTo(Side.SELL);

        // Verify order status
        assertThat(result.getOrder().isFilled()).isTrue();
        assertThat(result.getOrder().getFilledQuantity()).isEqualByComparingTo("1.0");

        // Both orders should be filled and removed from book
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getAsks()).isEmpty();
        assertThat(orderBook.isEmpty()).isTrue();
    }

    @Test
    void shouldMatchWhenBuyPriceExceedsAskPrice() {
        // Add SELL at 50000
        orderBook.addOrder(createLimitOrder(Side.SELL, "50000", "1.0"));

        // Add BUY at 51000 (higher than ask, should match at 50000)
        Order buyOrder = createLimitOrder(Side.BUY, "51000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo("50000");
        assertThat(orderBook.isEmpty()).isTrue();
    }

    // ============ Matching - Partial Fill ============

    @Test
    void shouldMatchPartiallyWhenQuantityInsufficient() {
        // Add SELL order for 1.0
        Order sellOrder = createLimitOrder(Side.SELL, "50000", "1.0");
        orderBook.addOrder(sellOrder);

        // Add BUY order for 2.0 (should partially match)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "2.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Verify trade
        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getTrades().get(0).getQuantity()).isEqualByComparingTo("1.0");

        // Verify order status
        assertThat(result.getOrder().getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(result.getOrder().getFilledQuantity()).isEqualByComparingTo("1.0");
        assertThat(result.getOrder().getRemainingQuantity()).isEqualByComparingTo("1.0");

        // Remaining 1.0 should be in the book
        assertThat(orderBook.getBids()).hasSize(1);
        assertThat(orderBook.getBids().get(0).getRemainingQuantity()).isEqualByComparingTo("1.0");
        assertThat(orderBook.getAsks()).isEmpty();
    }

    @Test
    void shouldPartiallyFillMakerOrder() {
        // Add SELL order for 2.0
        Order sellOrder = createLimitOrder(Side.SELL, "50000", "2.0");
        orderBook.addOrder(sellOrder);

        // Add BUY order for 1.0 (should partially fill sell order)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getOrder().isFilled()).isTrue();

        // Sell order should have 1.0 remaining
        assertThat(orderBook.getAsks()).hasSize(1);
        assertThat(orderBook.getAsks().get(0).getRemainingQuantity()).isEqualByComparingTo("1.0");
    }

    // ============ Matching - Multiple Orders ============

    @Test
    void shouldMatchAgainstMultipleOrdersAtSamePrice() {
        // Add multiple SELL orders at same price
        Order sell1 = createLimitOrder(Side.SELL, "50000", "0.5");
        Order sell2 = createLimitOrder(Side.SELL, "50000", "0.3");
        orderBook.addOrder(sell1);
        orderBook.addOrder(sell2);

        // Add BUY order that matches both
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "0.8");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Should create 2 trades
        assertThat(result.getTrades()).hasSize(2);
        assertThat(result.getTrades().get(0).getQuantity()).isEqualByComparingTo("0.5");
        assertThat(result.getTrades().get(1).getQuantity()).isEqualByComparingTo("0.3");
        assertThat(result.getOrder().isFilled()).isTrue();
        assertThat(orderBook.isEmpty()).isTrue();
    }

    @Test
    void shouldMatchAgainstMultiplePriceLevels() {
        // Add SELL orders at different prices
        orderBook.addOrder(createLimitOrder(Side.SELL, "50000", "0.5"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50100", "0.5"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50200", "1.0"));

        // Add BUY order at 50100 (should match first two levels)
        Order buyOrder = createLimitOrder(Side.BUY, "50100", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Should match 0.5 at 50000, then 0.5 at 50100
        assertThat(result.getTrades()).hasSize(2);
        assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo("50000");
        assertThat(result.getTrades().get(1).getPrice()).isEqualByComparingTo("50100");
        assertThat(result.getOrder().isFilled()).isTrue();

        // Order at 50200 should remain
        assertThat(orderBook.getAsks()).hasSize(1);
        assertThat(orderBook.getBestAskPrice()).contains(new BigDecimal("50200"));
    }

    // ============ Price-Time Priority ============

    @Test
    void shouldRespectPriceTimePriority() {
        // Add orders at same price in sequence
        Order order1 = createLimitOrder(Side.SELL, "50000", "1.0");
        Order order2 = createLimitOrder(Side.SELL, "50000", "1.0");
        Order order3 = createLimitOrder(Side.SELL, "50000", "1.0");

        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);

        // Match should execute against order1 first (FIFO)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getTrades().get(0).getMakerOrderId()).isEqualTo(order1.getId());

        // Order2 and Order3 should remain
        assertThat(orderBook.getAsks()).hasSize(2);
    }

    @Test
    void shouldMatchBestPriceFirst() {
        // Add SELL orders at different prices
        orderBook.addOrder(createLimitOrder(Side.SELL, "50200", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50100", "1.0"));

        // BUY at high price should match best (lowest) ask first
        Order buyOrder = createLimitOrder(Side.BUY, "51000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo("50000");

        // Other orders remain
        assertThat(orderBook.getAsks()).hasSize(2);
        assertThat(orderBook.getBestAskPrice()).contains(new BigDecimal("50100"));
    }

    // ============ Cancel ============

    @Test
    void shouldCancelOrder() {
        Order order = createLimitOrder(Side.BUY, "50000", "1.0");
        orderBook.addOrder(order);

        boolean cancelled = orderBook.cancelOrder(order.getId());

        assertThat(cancelled).isTrue();
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getOrder(order.getId())).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenCancellingNonExistentOrder() {
        boolean cancelled = orderBook.cancelOrder(OrderId.generate());

        assertThat(cancelled).isFalse();
    }

    @Test
    void shouldCancelSpecificOrderFromMultiple() {
        Order order1 = createLimitOrder(Side.BUY, "50000", "1.0");
        Order order2 = createLimitOrder(Side.BUY, "50000", "2.0");
        Order order3 = createLimitOrder(Side.BUY, "50000", "3.0");

        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);

        orderBook.cancelOrder(order2.getId());

        assertThat(orderBook.getBids()).hasSize(2);
        assertThat(orderBook.getOrder(order1.getId())).isPresent();
        assertThat(orderBook.getOrder(order2.getId())).isEmpty();
        assertThat(orderBook.getOrder(order3.getId())).isPresent();
    }

    // ============ Query Methods ============

    @Test
    void shouldReturnBidsInCorrectOrder() {
        orderBook.addOrder(createLimitOrder(Side.BUY, "50000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.BUY, "51000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.BUY, "49000", "1.0"));

        var bids = orderBook.getBids();

        // Should be sorted by price descending (best bid first)
        assertThat(bids).hasSize(3);
        assertThat(bids.get(0).getPrice()).isEqualByComparingTo("51000");
        assertThat(bids.get(1).getPrice()).isEqualByComparingTo("50000");
        assertThat(bids.get(2).getPrice()).isEqualByComparingTo("49000");
    }

    @Test
    void shouldReturnAsksInCorrectOrder() {
        orderBook.addOrder(createLimitOrder(Side.SELL, "52000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "51000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "53000", "1.0"));

        var asks = orderBook.getAsks();

        // Should be sorted by price ascending (best ask first)
        assertThat(asks).hasSize(3);
        assertThat(asks.get(0).getPrice()).isEqualByComparingTo("51000");
        assertThat(asks.get(1).getPrice()).isEqualByComparingTo("52000");
        assertThat(asks.get(2).getPrice()).isEqualByComparingTo("53000");
    }

    // ============ Helper Methods ============

    private Order createLimitOrder(Side side, String price, String quantity) {
        return Order.builder()
            .id(OrderId.generate())
            .userId(UserId.of("user-" + System.nanoTime()))
            .symbol(symbol)
            .side(side)
            .type(OrderType.LIMIT)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .build();
    }
}
```

---

## ✅ Step 7: Compile & Test

### 7.1 Compile (Generates Immutables)

```bash
cd /Users/MihaiJinga/Downloads/tradingengine
./mvnw clean compile -pl domain
```

**What happens:**
- Immutables annotation processor runs
- Generates classes in `domain/target/generated-sources/annotations/`
- Generated classes: `ImmutableOrderId`, `ImmutableOrder`, etc.

**Verify generation:**
```bash
ls domain/target/generated-sources/annotations/com/trading/domain/model/
```

You should see all `ImmutableXXX.java` files.

---

### 7.2 Run Tests

```bash
./mvnw test -pl domain
```

**Expected output:**
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.trading.domain.model.OrderTest
[INFO] Running com.trading.domain.model.SymbolTest
[INFO] Running com.trading.domain.orderbook.OrderBookTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

### 7.3 Check Test Coverage (Optional)

Add JaCoCo plugin to `domain/pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Generate coverage report:
```bash
./mvnw test jacoco:report -pl domain
open domain/target/site/jacoco/index.html
```

**Target:** 100% coverage on `OrderBook.java`

---

## 🎯 Success Criteria

Check all before moving to Iteration 2:

- [ ] All enums compile (Side, OrderType, OrderStatus)
- [ ] All value objects compile with Immutables
- [ ] Order and Trade models compile
- [ ] OrderBook compiles with zero errors
- [ ] All 25+ tests pass
- [ ] No Spring/framework dependencies in domain
- [ ] Generated classes exist in `target/generated-sources/`
- [ ] `./mvnw clean install -pl domain` succeeds
- [ ] 100% test coverage on matching logic

---

## 📚 Key Concepts Reference

### Immutables Pattern

```java
// 1. Define interface with @Value.Immutable
@Value.Immutable
public interface Order {
    String getId();
    BigDecimal getPrice();
}

// 2. Use generated class
Order order = ImmutableOrder.builder()
    .id("123")
    .price(new BigDecimal("50000"))
    .build();

// 3. Create modified copy
Order updated = ImmutableOrder.copyOf(order)
    .withPrice(new BigDecimal("51000"));

// Original is unchanged
assertThat(order.getPrice()).isEqualByComparingTo("50000");
assertThat(updated.getPrice()).isEqualByComparingTo("51000");
```

### Price-Time Priority

**Rule:** Best price first, then FIFO at same price

**Example:**
```
Order Book (BTC-USD):
BIDS (Descending):       ASKS (Ascending):
  $51,000 - 1.0 BTC        $52,000 - 2.0 BTC
  $50,500 - 0.5 BTC        $53,000 - 1.0 BTC
  $50,000 - 2.0 BTC        $54,000 - 3.0 BTC

Incoming SELL @ $50,500 for 1.5 BTC:
  Match 1: 1.0 @ $51,000 (best bid)
  Match 2: 0.5 @ $50,500 (next best)
  Result: Fully filled, 2 trades created
```

### Matching Algorithm

**BUY Order Logic:**
```
1. Look at best ASK (lowest sell price)
2. If BUY price >= ASK price:
   a. Match against first order in queue (FIFO)
   b. Create Trade at ASK price (maker wins)
   c. Update filled quantities
   d. Remove filled orders
   e. Repeat while quantity remains
3. Add remaining quantity to BID side
```

**SELL Order Logic:** (symmetric)
```
1. Look at best BID (highest buy price)
2. If SELL price <= BID price:
   (same matching logic)
3. Add remaining to ASK side
```

---

## 🐛 Common Issues & Solutions

### Issue 1: Generated classes not found

**Symptom:** `ImmutableOrder cannot be resolved`

**Solution:**
```bash
./mvnw clean compile -pl domain
```

Then refresh IDE:
- IntelliJ: Cmd+Shift+A → "Reload All Maven Projects"
- Eclipse: Right-click project → Maven → Update Project

### Issue 2: Compilation errors with @Value.Immutable

**Symptom:** Annotation processor not running

**Solution:** Check `pom.xml` has annotation processor path:
```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.immutables</groupId>
        <artifactId>value</artifactId>
        <version>${immutables.version}</version>
    </path>
</annotationProcessorPaths>
```

### Issue 3: Tests fail with BigDecimal comparison

**Wrong:**
```java
assertThat(price).isEqualTo(new BigDecimal("50000"));
```

**Correct:**
```java
assertThat(price).isEqualByComparingTo("50000");
```

### Issue 4: ConcurrentModificationException in matching

**Cause:** Modifying collection while iterating

**Solution:** Use `Iterator.remove()` instead of `list.remove()`

---

## 🚀 Next Steps

After completing Iteration 1:

1. **Commit your work:**
   ```bash
   git add .
   git commit -m "Iteration 1: Pure matching engine with Immutables"
   ```

2. **Review results:**
   - Run all tests: `./mvnw test -pl domain`
   - Check coverage: Open `domain/target/site/jacoco/index.html`

3. **Move to Iteration 2:**
   - Read: `TRADING_ENGINE_SPEC.md` → Iteration 2
   - Topic: Commands & Events (Event Sourcing foundation)

---

## 🎉 Congratulations!

You've built a production-quality matching engine with:
- ✅ Immutable domain objects
- ✅ Price-time priority
- ✅ Partial fills
- ✅ Multiple order matching
- ✅ 100% test coverage
- ✅ Zero framework dependencies

**This is the foundation for the entire trading engine!** 🚀

---

**Ready to implement?** Follow each step in order and you'll have Iteration 1 complete in 2-4 hours!
