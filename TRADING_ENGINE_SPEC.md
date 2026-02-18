# Trading Engine — Implementation Specification

**Event-Sourced Trading Engine with CQRS**

A production-grade matching engine built with event sourcing, CQRS, and clean architecture principles.

---

## 🎯 Core Use Cases

### UC1 — Place Order
**Input:** Symbol, Side (BUY/SELL), Quantity, Price, UserId
**Process:**
1. Validate order (sufficient funds, valid symbol, positive quantity)
2. Reserve funds (for BUY) or assets (for SELL)
3. Add to order book
4. Attempt matching
5. Emit events

**Events Emitted:**
- `OrderPlaced`
- `TradeExecuted` (if matched)
- `OrderPartiallyFilled` / `OrderFullyFilled`
- `FundsReserved`

### UC2 — Cancel Order
**Input:** OrderId, UserId
**Process:**
1. Validate ownership
2. Remove from order book
3. Release reserved funds/assets

**Events Emitted:**
- `OrderCancelled`
- `FundsReleased`

### UC3 — Matching Engine
**Core Logic:**
- Maintain separate bid (BUY) and ask (SELL) order books per symbol
- Price-time priority (best price first, then FIFO)
- Atomic matching: when orders cross, execute trades immediately
- Support partial fills

**Outputs:**
- `TradeExecuted` events with maker/taker details
- Updated order book state

### UC4 — Maintain Positions & Balances
**Process:**
- Listen to `TradeExecuted` events
- Update user balances (USD, BTC, etc.)
- Track realized PnL
- Maintain position history

### UC5 — Query State (CQRS Read Model)
**Queries:**
- Get order book depth (top N levels)
- Get user's open orders
- Get user's trade history
- Get user's positions
- Get recent trades for a symbol

### UC6 — Replay & Recovery
**Process:**
- Load all events from event store
- Replay through aggregates
- Rebuild order books deterministically
- Verify consistency with projections

---

## 🧱 Architectural Decisions

### ✅ 1. Event Sourcing
**Decision:** Store events as source of truth, not mutable state.

**Event Store Schema:**
```sql
CREATE TABLE events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_id    VARCHAR(255) NOT NULL,    -- e.g., "BTC-USD", "user-123"
    aggregate_type  VARCHAR(100) NOT NULL,    -- e.g., "OrderBook", "Position"
    event_type      VARCHAR(100) NOT NULL,    -- e.g., "OrderPlaced"
    event_data      JSONB NOT NULL,
    version         INT NOT NULL,             -- Optimistic locking
    timestamp       TIMESTAMP NOT NULL,
    metadata        JSONB,
    UNIQUE(aggregate_id, version)
);
CREATE INDEX idx_events_aggregate ON events(aggregate_id, version);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_timestamp ON events(timestamp);
```

**Why:**
- Full audit trail
- Time travel debugging
- Regulatory compliance
- Deterministic replay

### ✅ 2. CQRS (Command Query Responsibility Segregation)

**Write Side:**
- Commands → Aggregates → Events
- Optimized for consistency and business rules
- No queries allowed

**Read Side:**
- Events → Projections (denormalized tables)
- Optimized for query performance
- Eventually consistent

**Projection Examples:**
```sql
-- Order Book Projection (fast reads)
CREATE TABLE order_book_view (
    symbol      VARCHAR(20) NOT NULL,
    side        VARCHAR(4) NOT NULL,
    price       NUMERIC(20, 8) NOT NULL,
    quantity    NUMERIC(20, 8) NOT NULL,
    order_count INT NOT NULL,
    PRIMARY KEY (symbol, side, price)
);

-- User Positions Projection
CREATE TABLE user_positions (
    user_id     VARCHAR(100) NOT NULL,
    asset       VARCHAR(20) NOT NULL,
    available   NUMERIC(20, 8) NOT NULL,
    reserved    NUMERIC(20, 8) NOT NULL,
    total       NUMERIC(20, 8) NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, asset)
);

-- Trade History Projection
CREATE TABLE trades_view (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(20) NOT NULL,
    price           NUMERIC(20, 8) NOT NULL,
    quantity        NUMERIC(20, 8) NOT NULL,
    buyer_id        VARCHAR(100) NOT NULL,
    seller_id       VARCHAR(100) NOT NULL,
    maker_order_id  VARCHAR(100) NOT NULL,
    taker_order_id  VARCHAR(100) NOT NULL,
    executed_at     TIMESTAMP NOT NULL
);
CREATE INDEX idx_trades_symbol ON trades_view(symbol, executed_at DESC);
CREATE INDEX idx_trades_user ON trades_view(buyer_id, executed_at DESC);
```

### ✅ 3. Immutable Domain Model

Use **Java records** with **Guava collections** for all domain objects:

```java
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
    // Validation in compact constructor
    public Order {
        if (orderId == null) throw new IllegalArgumentException("OrderId cannot be null");
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        // ... more validation
    }

    // Helper methods
    public BigDecimal getRemainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    public boolean isFilled() {
        return filledQuantity.compareTo(quantity) == 0;
    }

    // Factory method
    public static Order create(OrderId orderId, UserId userId, Symbol symbol,
                              Side side, OrderType type, BigDecimal price, BigDecimal quantity) {
        return new Order(orderId, userId, symbol, side, type, price, quantity,
                        BigDecimal.ZERO, OrderStatus.OPEN, Instant.now());
    }

    // Copy with modifications
    public Order withFilledQuantity(BigDecimal newFilled) {
        OrderStatus newStatus = newFilled.compareTo(quantity) == 0 ? OrderStatus.FILLED
                              : newFilled.compareTo(BigDecimal.ZERO) > 0 ? OrderStatus.PARTIALLY_FILLED
                              : OrderStatus.OPEN;
        return new Order(orderId, userId, symbol, side, type,
                        price, quantity, newFilled, newStatus, createdAt);
    }
}

// Usage:
Order order = Order.create(orderId, userId, Symbol.of("BTC-USD"),
    Side.BUY, OrderType.LIMIT,
    new BigDecimal("50000.00"), new BigDecimal("1.5"));
```

**Collections - Use Guava Immutables:**
```java
// Instead of List<Order> → use ImmutableList<Order>
ImmutableList<Order> orders = ImmutableList.of(order1, order2);

// Instead of Map<Symbol, OrderBook> → use ImmutableMap
ImmutableMap<Symbol, OrderBook> books = ImmutableMap.of(
    symbol1, orderBook1,
    symbol2, orderBook2
);

// For building collections:
ImmutableList<Trade> trades = ImmutableList.<Trade>builder()
    .add(trade1)
    .add(trade2)
    .build();
```

**Why:**
- Thread-safe by default
- No accidental mutation
- Builder pattern (easier than constructors)
- Works seamlessly with Jackson for JSON serialization
- Excellent for concurrent systems
- Guava collections provide immutability guarantees

### ✅ 4. Aggregate Root = OrderBook per Symbol

**One aggregate per trading pair** (e.g., `BTC-USD`)

```java
public class OrderBookAggregate {
    private final Symbol symbol;
    // Internal mutable state (not exposed)
    private final TreeMap<BigDecimal, LinkedList<Order>> bids;  // Descending
    private final TreeMap<BigDecimal, LinkedList<Order>> asks;  // Ascending
    private int version;

    public ImmutableList<DomainEvent> handle(PlaceOrderCommand cmd) {
        // 1. Validate
        // 2. Add to book
        // 3. Match
        // 4. Return immutable list of events
        return ImmutableList.of(/* events */);
    }

    // Query methods return immutable copies
    public ImmutableList<Order> getBids() {
        return ImmutableList.copyOf(
            bids.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );
    }
}
```

**Concurrency Strategy:**
- One aggregate instance per symbol loaded into memory
- Internal state is mutable (for performance) but never exposed
- All public methods return immutable collections (Guava)
- Optimistic locking using version number
- Commands processed sequentially per aggregate (single-threaded)
- Different symbols can process in parallel

### ✅ 5. Command-Event Flow

```
REST Controller (DTO)
    ↓
Application Service (maps to Command)
    ↓
Command Bus / Handler
    ↓
Aggregate.handle(Command)
    ↓ (returns)
List<DomainEvent>
    ↓
Event Store (persist)
    ↓
Event Dispatcher
    ↓
Projections (async update)
```

**Key Interfaces:**

```java
// Marker interface for commands
public interface Command {
    AggregateId getAggregateId();
}

// Marker interface for domain events
public interface DomainEvent {
    UUID getEventId();
    AggregateId getAggregateId();
    Instant getOccurredAt();
}

// Event Store interface using Guava collections
public interface EventStore {
    void save(AggregateId id, ImmutableList<DomainEvent> events, int expectedVersion);
    ImmutableList<DomainEvent> loadEvents(AggregateId id);
    ImmutableList<DomainEvent> loadAllEvents();
}
```

### ✅ 6. Deterministic Matching Logic

**Pure function:**
```
(OrderBook State, PlaceOrderCommand) → (New State, List<Events>)
```

**Matching Algorithm:**
1. If BUY order:
   - Check lowest ASK price
   - If BUY price >= ASK price → execute trade
   - Repeat until no match or order filled
2. If SELL order:
   - Check highest BID price
   - If SELL price <= BID price → execute trade
   - Repeat until no match or order filled

**Price-Time Priority:**
- Orders at same price level: FIFO queue
- Better prices matched first

### ✅ 7. Infrastructure Isolation

**Core Domain (pure Java):**
- No Spring annotations
- No JPA/Hibernate
- No framework dependencies
- Just: `java.math`, `java.time`, `java.util`

**Infrastructure Layer:**
- Spring Boot REST controllers
- PostgreSQL event store implementation
- Kafka event publisher
- Flyway migrations

**Dependency Rule:**
```
Domain ← Application ← Infrastructure
   ↑          ↑              ↑
 (no deps) (domain only) (everything)
```

### ✅ 8. Snapshots (Iteration 7)

For performance, periodically snapshot aggregate state:

```sql
CREATE TABLE snapshots (
    aggregate_id    VARCHAR(255) PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    snapshot_data   JSONB NOT NULL,
    version         INT NOT NULL,
    created_at      TIMESTAMP NOT NULL
);
```

**Strategy:**
- Snapshot every 100 events
- On load: get latest snapshot + subsequent events
- Reduces replay time from O(n) to O(100)

---

## 📦 Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Language | Java 21 | Records, pattern matching, virtual threads, sealed classes |
| Immutability | Java Records | Immutable value objects and domain models |
| Collections | Guava | ImmutableList, ImmutableMap, ImmutableSet |
| Framework | Spring Boot 3.2+ | REST, DI (infrastructure only) |
| Database | PostgreSQL 16 | Event store + projections |
| Migration | Flyway | Schema versioning |
| Messaging | Kafka | External event publishing (Iteration 9) |
| Testing | JUnit 5, AssertJ, Testcontainers | Unit + integration tests |
| Build | Maven | Multi-module dependency management |
| JSON | Jackson | Event serialization (works with records) |

---

## 🚀 Iteration Plan

### 🥇 Iteration 1 — Pure Matching Engine (No Framework)

**Goal:** Build core domain logic with zero dependencies.

**Deliverables:**
1. **Domain Objects** (Java Records + Guava):
   - `OrderId`, `UserId`, `Symbol` - value object records with validation
   - `Side`, `OrderType`, `OrderStatus` - enums
   - `Order` - record with validation and helper methods
   - `Trade` - record representing executed trades

2. **OrderBook Class:**
   ```java
   public class OrderBook {
       private final Symbol symbol;
       // Internal mutable state for performance
       private final TreeMap<BigDecimal, LinkedList<Order>> bids;  // Descending
       private final TreeMap<BigDecimal, LinkedList<Order>> asks;  // Ascending

       public MatchResult addOrder(Order order);
       public boolean cancelOrder(OrderId orderId);
       public ImmutableList<Trade> match(Order incomingOrder);

       // Query methods return immutable collections
       public ImmutableList<Order> getBids();
       public ImmutableList<Order> getAsks();
   }
   ```

3. **Matching Logic:**
   - Price-time priority
   - Partial fill support
   - Pure functions (no side effects)

4. **Unit Tests:**
   - Test matching scenarios:
     - Full match
     - Partial match
     - No match
     - Multiple matches at same price
   - Property-based tests (order book invariants)

**Exit Criteria:**
- [ ] All unit tests pass
- [ ] 100% code coverage on matching logic
- [ ] No external dependencies

**Package Structure:**
```
src/main/java/
└── com.trading.domain/
    ├── model/
    │   ├── Order.java
    │   ├── Trade.java
    │   └── ...
    └── orderbook/
        └── OrderBook.java
```

---

### 🥈 Iteration 2 — Introduce Commands & Events

**Goal:** Add event sourcing foundation (still in-memory).

**Deliverables:**

1. **Command Objects:**
   ```java
   public record PlaceOrderCommand(
       OrderId orderId,
       UserId userId,
       Symbol symbol,
       Side side,
       OrderType type,
       BigDecimal price,
       BigDecimal quantity
   ) {
       public PlaceOrderCommand {
           if (orderId == null) throw new IllegalArgumentException("OrderId cannot be null");
           if (userId == null) throw new IllegalArgumentException("UserId cannot be null");
           if (symbol == null) throw new IllegalArgumentException("Symbol cannot be null");
           if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
               throw new IllegalArgumentException("Price must be positive");
           }
           if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
               throw new IllegalArgumentException("Quantity must be positive");
           }
       }
   }

   public record CancelOrderCommand(
       OrderId orderId,
       UserId userId,
       Symbol symbol
   ) {
       public CancelOrderCommand {
           if (orderId == null) throw new IllegalArgumentException("OrderId cannot be null");
           if (userId == null) throw new IllegalArgumentException("UserId cannot be null");
           if (symbol == null) throw new IllegalArgumentException("Symbol cannot be null");
       }
   }
   ```

2. **Domain Events:**
   ```java
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
           // ... more validation
       }

       public static OrderPlacedEvent create(AggregateId aggregateId, OrderId orderId,
                                            UserId userId, Symbol symbol, Side side,
                                            OrderType type, BigDecimal price, BigDecimal quantity) {
           return new OrderPlacedEvent(UUID.randomUUID().toString(), aggregateId,
                                      Instant.now(), orderId, userId, symbol, side, type, price, quantity);
       }

       @Override
       public String getEventId() { return eventId; }

       @Override
       public AggregateId getAggregateId() { return aggregateId; }

       @Override
       public Instant getOccurredAt() { return occurredAt; }

       @Override
       public String getEventType() { return "OrderPlaced"; }
   }

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
       Instant getOccurredAt();

       @Override
       default AggregateId getAggregateId() {
           return AggregateId.of(getSymbol());
       }
   }

   public record OrderCancelledEvent(...) implements DomainEvent { /* ... */ }

   public record OrderFilledEvent(...) implements DomainEvent { /* ... */ }
   ```

3. **OrderBookAggregate:**
   ```java
   public class OrderBookAggregate {
       private final Symbol symbol;
       private final OrderBook orderBook;
       private int version = 0;

       public ImmutableList<DomainEvent> handle(PlaceOrderCommand cmd) {
           // Business logic + validation
           // Return immutable list of events (don't mutate state yet)
           return ImmutableList.<DomainEvent>builder()
               .add(orderPlacedEvent)
               .addAll(tradeEvents)
               .build();
       }

       public void apply(DomainEvent event) {
           // Apply event to internal state
           // Used during replay
           version++;
       }
   }
   ```

4. **Tests:**
   - Command handling produces correct events
   - Replaying events reconstructs state
   - Event immutability

**Exit Criteria:**
- [ ] Commands produce events
- [ ] Events can be applied to rebuild state
- [ ] Tests verify event-driven logic

---

### 🥉 Iteration 3 — In-Memory Event Store

**Goal:** Store and replay events (no database yet).

**Deliverables:**

1. **Event Store Interface:**
   ```java
   public interface EventStore {
       void save(AggregateId aggregateId,
                 ImmutableList<DomainEvent> events,
                 int expectedVersion);
       ImmutableList<DomainEvent> loadEvents(AggregateId aggregateId);
       ImmutableList<DomainEvent> loadAllEvents();
   }
   ```

2. **In-Memory Implementation:**
   ```java
   public class InMemoryEventStore implements EventStore {
       // Internal mutable map for storage
       private final Map<AggregateId, List<StoredEvent>> eventStreams = new ConcurrentHashMap<>();

       @Override
       public ImmutableList<DomainEvent> loadEvents(AggregateId aggregateId) {
           List<StoredEvent> events = eventStreams.getOrDefault(aggregateId, List.of());
           return ImmutableList.copyOf(events.stream()
               .map(StoredEvent::getEvent)
               .collect(Collectors.toList()));
       }

       // Optimistic locking check on save()
   }
   ```

3. **Event Dispatcher:**
   ```java
   public class EventDispatcher {
       private final ImmutableList<EventListener> listeners;

       public EventDispatcher(ImmutableList<EventListener> listeners) {
           this.listeners = listeners;
       }

       public void dispatch(DomainEvent event) {
           listeners.forEach(l -> l.handle(event));
       }
   }
   ```

4. **Aggregate Repository:**
   ```java
   public class OrderBookRepository {
       private final EventStore eventStore;
       private final EventDispatcher eventDispatcher;

       public OrderBookAggregate load(Symbol symbol) {
           ImmutableList<DomainEvent> events = eventStore.loadEvents(symbol);
           OrderBookAggregate aggregate = new OrderBookAggregate(symbol);
           events.forEach(aggregate::apply);
           return aggregate;
       }

       public void save(OrderBookAggregate aggregate,
                       ImmutableList<DomainEvent> newEvents) {
           eventStore.save(aggregate.getId(), newEvents, aggregate.getVersion());
           newEvents.forEach(eventDispatcher::dispatch);
       }
   }
   ```

5. **Integration Tests:**
   - Save events → load aggregate → verify state
   - Replay all events → verify deterministic result
   - Test optimistic locking (concurrent modifications)

**Exit Criteria:**
- [ ] Events persisted in-memory
- [ ] Aggregates reconstructed from events
- [ ] Replay tests pass

---

### 🏅 Iteration 4 — Spring Boot Integration

**Goal:** Add REST API and Spring infrastructure.

**Deliverables:**

1. **Project Setup:**
   ```xml
   <dependencies>
       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-web</artifactId>
       </dependency>
       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-validation</artifactId>
       </dependency>
   </dependencies>
   ```

2. **REST Controllers:**
   ```java
   @RestController
   @RequestMapping("/api/orders")
   public class OrderController {
       private final OrderApplicationService orderService;

       @PostMapping
       public ResponseEntity<OrderResponse> placeOrder(
           @Valid @RequestBody PlaceOrderRequest request
       ) {
           // Map DTO → Command
           // Handle command
           // Return response
       }

       @DeleteMapping("/{orderId}")
       public ResponseEntity<Void> cancelOrder(@PathVariable String orderId) {
           // ...
       }
   }
   ```

3. **Application Service:**
   ```java
   @Service
   public class OrderApplicationService {
       private final OrderBookRepository repository;

       @Transactional
       public OrderId placeOrder(PlaceOrderCommand command) {
           OrderBookAggregate aggregate = repository.load(command.symbol());
           ImmutableList<DomainEvent> events = aggregate.handle(command);
           repository.save(aggregate, events);
           return command.orderId();
       }
   }
   ```

4. **DTOs (input/output) - Use Java records for REST DTOs:**
   ```java
   public record PlaceOrderRequest(
       @NotNull String symbol,
       @NotNull String side,
       @NotNull @Positive BigDecimal price,
       @NotNull @Positive BigDecimal quantity
   ) {}

   public record OrderResponse(
       String orderId,
       String status,
       Instant createdAt
   ) {}
   ```

5. **Query Endpoints (read from in-memory projection):**
   ```java
   @GetMapping("/orderbook/{symbol}")
   public OrderBookResponse getOrderBook(@PathVariable String symbol) {
       // Return top 10 bids/asks
   }
   ```

6. **Tests:**
   - `@SpringBootTest` integration tests
   - REST API tests (MockMvc)
   - End-to-end: POST order → GET order book

**Exit Criteria:**
- [ ] REST API functional
- [ ] In-memory event store still used
- [ ] Integration tests pass

**Package Structure:**
```
src/main/java/
├── com.trading.domain/           # Pure domain (no Spring)
├── com.trading.application/      # Application services
└── com.trading.infrastructure/   # Spring controllers, config
    ├── rest/
    ├── config/
    └── eventstore/
```

---

### 🏅 Iteration 5 — PostgreSQL Event Store + Flyway

**Goal:** Persist events to PostgreSQL, survive restarts.

**Deliverables:**

1. **Dependencies:**
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-jdbc</artifactId>
   </dependency>
   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
   </dependency>
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-core</artifactId>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>postgresql</artifactId>
       <scope>test</scope>
   </dependency>
   ```

2. **Flyway Migration (V1):**
   ```sql
   -- V1__create_events_table.sql
   CREATE TABLE events (
       id              BIGSERIAL PRIMARY KEY,
       aggregate_id    VARCHAR(255) NOT NULL,
       aggregate_type  VARCHAR(100) NOT NULL,
       event_type      VARCHAR(100) NOT NULL,
       event_data      JSONB NOT NULL,
       version         INT NOT NULL,
       timestamp       TIMESTAMP NOT NULL DEFAULT NOW(),
       metadata        JSONB,
       CONSTRAINT unique_version UNIQUE (aggregate_id, version)
   );

   CREATE INDEX idx_events_aggregate ON events(aggregate_id, version);
   CREATE INDEX idx_events_type ON events(event_type);
   CREATE INDEX idx_events_timestamp ON events(timestamp DESC);
   ```

3. **PostgreSQL Event Store Implementation:**
   ```java
   @Repository
   public class PostgresEventStore implements EventStore {
       private final JdbcTemplate jdbcTemplate;
       private final ObjectMapper objectMapper;  // Jackson for JSON

       @Override
       public void save(AggregateId id, ImmutableList<DomainEvent> events, int expectedVersion) {
           // 1. Check version (optimistic locking)
           int currentVersion = getCurrentVersion(id);
           if (currentVersion != expectedVersion) {
               throw new ConcurrencyException("Version mismatch");
           }

           // 2. Insert events with version = expectedVersion + 1, + 2, ...
           int version = expectedVersion;
           for (DomainEvent event : events) {
               version++;
               String eventType = event.getClass().getName();
               String eventData = objectMapper.writeValueAsString(event);

               jdbcTemplate.update(
                   "INSERT INTO events (aggregate_id, aggregate_type, event_type, event_data, version, timestamp) " +
                   "VALUES (?, ?, ?, ?::jsonb, ?, ?)",
                   id.getValue(), id.getType(), eventType, eventData, version, event.getOccurredAt()
               );
           }
       }

       @Override
       public ImmutableList<DomainEvent> loadEvents(AggregateId id) {
           List<DomainEvent> events = jdbcTemplate.query(
               "SELECT event_type, event_data FROM events " +
               "WHERE aggregate_id = ? ORDER BY version ASC",
               (rs, rowNum) -> {
                   String eventType = rs.getString("event_type");
                   String eventData = rs.getString("event_data");
                   return objectMapper.readValue(eventData, Class.forName(eventType));
               },
               id.getValue()
           );
           return ImmutableList.copyOf(events);
       }
   }
   ```

4. **Event Serialization:**
   - Store `event_type` = fully qualified class name
   - Store `event_data` = JSON
   - Use Jackson `@JsonTypeInfo` for polymorphic deserialization

5. **Integration Tests (Testcontainers):**
   ```java
   @SpringBootTest
   @Testcontainers
   class PostgresEventStoreTest {
       @Container
       static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

       @Test
       void shouldPersistAndLoadEvents() {
           // Test event round-trip
       }

       @Test
       void shouldEnforceOptimisticLocking() {
           // Simulate concurrent modification
       }
   }
   ```

6. **Application Startup:**
   - On startup, load all aggregates from events
   - Rebuild order books in memory
   - Ready to handle commands

**Exit Criteria:**
- [ ] Events persisted to PostgreSQL
- [ ] Restart application → state recovered
- [ ] Optimistic locking works
- [ ] Testcontainers tests pass

---

### 🏅 Iteration 6 — CQRS Projections

**Goal:** Build read models for fast queries.

**Deliverables:**

1. **Flyway Migration (V2):**
   ```sql
   -- V2__create_projections.sql

   -- Order Book View (denormalized for fast reads)
   CREATE TABLE order_book_view (
       symbol      VARCHAR(20) NOT NULL,
       side        VARCHAR(4) NOT NULL,   -- 'BUY' or 'SELL'
       price       NUMERIC(20, 8) NOT NULL,
       quantity    NUMERIC(20, 8) NOT NULL,
       order_count INT NOT NULL DEFAULT 0,
       updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
       PRIMARY KEY (symbol, side, price)
   );

   -- User Positions
   CREATE TABLE user_positions (
       user_id     VARCHAR(100) NOT NULL,
       asset       VARCHAR(20) NOT NULL,
       available   NUMERIC(20, 8) NOT NULL DEFAULT 0,
       reserved    NUMERIC(20, 8) NOT NULL DEFAULT 0,
       total       NUMERIC(20, 8) NOT NULL DEFAULT 0,
       updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
       PRIMARY KEY (user_id, asset)
   );

   -- Open Orders View
   CREATE TABLE open_orders_view (
       order_id        VARCHAR(100) PRIMARY KEY,
       user_id         VARCHAR(100) NOT NULL,
       symbol          VARCHAR(20) NOT NULL,
       side            VARCHAR(4) NOT NULL,
       price           NUMERIC(20, 8) NOT NULL,
       quantity        NUMERIC(20, 8) NOT NULL,
       filled_quantity NUMERIC(20, 8) NOT NULL DEFAULT 0,
       status          VARCHAR(20) NOT NULL,
       created_at      TIMESTAMP NOT NULL
   );
   CREATE INDEX idx_open_orders_user ON open_orders_view(user_id, created_at DESC);

   -- Trade History
   CREATE TABLE trades_view (
       id              BIGSERIAL PRIMARY KEY,
       symbol          VARCHAR(20) NOT NULL,
       price           NUMERIC(20, 8) NOT NULL,
       quantity        NUMERIC(20, 8) NOT NULL,
       buyer_id        VARCHAR(100) NOT NULL,
       seller_id       VARCHAR(100) NOT NULL,
       maker_order_id  VARCHAR(100) NOT NULL,
       taker_order_id  VARCHAR(100) NOT NULL,
       executed_at     TIMESTAMP NOT NULL
   );
   CREATE INDEX idx_trades_symbol ON trades_view(symbol, executed_at DESC);
   CREATE INDEX idx_trades_buyer ON trades_view(buyer_id, executed_at DESC);
   CREATE INDEX idx_trades_seller ON trades_view(seller_id, executed_at DESC);
   ```

2. **Event Listeners (Projections):**
   ```java
   @Component
   public class OrderBookProjection implements EventListener {
       private final JdbcTemplate jdbcTemplate;

       @EventHandler
       public void on(OrderPlacedEvent event) {
           // INSERT or UPDATE order_book_view
           // INSERT into open_orders_view
       }

       @EventHandler
       public void on(TradeExecutedEvent event) {
           // UPDATE order_book_view (decrease quantity)
           // INSERT into trades_view
           // UPDATE open_orders_view (filled_quantity)
       }

       @EventHandler
       public void on(OrderCancelledEvent event) {
           // DELETE from order_book_view / open_orders_view
       }
   }

   @Component
   public class PositionProjection implements EventListener {
       @EventHandler
       public void on(TradeExecutedEvent event) {
           // UPDATE user_positions (buyer and seller)
       }

       @EventHandler
       public void on(FundsReservedEvent event) {
           // Update available vs reserved
       }
   }
   ```

3. **Query Services:**
   ```java
   @Service
   public class OrderBookQueryService {
       private final JdbcTemplate jdbcTemplate;

       public OrderBookDepth getOrderBook(Symbol symbol, int depth) {
           // SELECT from order_book_view
           // Return top N bids and asks
       }
   }

   @Service
   public class TradeQueryService {
       public List<Trade> getRecentTrades(Symbol symbol, int limit) {
           // SELECT from trades_view
       }

       public List<Trade> getUserTrades(UserId userId) {
           // SELECT WHERE buyer_id = ? OR seller_id = ?
       }
   }
   ```

4. **Query Endpoints:**
   ```java
   @RestController
   @RequestMapping("/api/query")
   public class QueryController {
       @GetMapping("/orderbook/{symbol}")
       public OrderBookDepth getOrderBook(@PathVariable String symbol) {
           return queryService.getOrderBook(Symbol.of(symbol), 10);
       }

       @GetMapping("/trades/{symbol}")
       public List<TradeDto> getRecentTrades(@PathVariable String symbol) {
           return queryService.getRecentTrades(Symbol.of(symbol), 50);
       }

       @GetMapping("/users/{userId}/positions")
       public List<PositionDto> getUserPositions(@PathVariable String userId) {
           return queryService.getUserPositions(UserId.of(userId));
       }
   }
   ```

5. **Eventual Consistency:**
   - Projections update asynchronously after events
   - Consider adding `@Async` on event handlers
   - Add projection rebuild endpoint (replay all events)

**Exit Criteria:**
- [ ] Projections update on events
- [ ] Query endpoints return fast results
- [ ] Projection rebuild works (replay)

---

### 🏅 Iteration 7 — Snapshots

**Goal:** Optimize aggregate loading with snapshots.

**Deliverables:**

1. **Flyway Migration (V3):**
   ```sql
   -- V3__create_snapshots_table.sql
   CREATE TABLE snapshots (
       aggregate_id    VARCHAR(255) PRIMARY KEY,
       aggregate_type  VARCHAR(100) NOT NULL,
       snapshot_data   JSONB NOT NULL,
       version         INT NOT NULL,
       created_at      TIMESTAMP NOT NULL DEFAULT NOW()
   );
   CREATE INDEX idx_snapshots_type ON snapshots(aggregate_type);
   ```

2. **Snapshot Strategy:**
   - Save snapshot every 100 events
   - Store entire `OrderBookAggregate` state as JSON
   - On load:
     - Fetch latest snapshot
     - Load events since snapshot version
     - Apply events

3. **Snapshot Repository:**
   ```java
   @Repository
   public class SnapshotRepository {
       public Optional<Snapshot> getLatestSnapshot(AggregateId id);
       public void saveSnapshot(AggregateId id, Object state, int version);
   }
   ```

4. **Updated Aggregate Repository:**
   ```java
   public OrderBookAggregate load(Symbol symbol) {
       // 1. Try to load snapshot
       Optional<Snapshot> snapshot = snapshotRepo.getLatestSnapshot(symbol);

       int fromVersion = 0;
       OrderBookAggregate aggregate;

       if (snapshot.isPresent()) {
           aggregate = deserializeSnapshot(snapshot.get());
           fromVersion = snapshot.get().getVersion();
       } else {
           aggregate = new OrderBookAggregate(symbol);
       }

       // 2. Load events since snapshot (returns ImmutableList)
       ImmutableList<DomainEvent> events = eventStore.loadEventsSince(symbol, fromVersion);
       events.forEach(aggregate::apply);

       return aggregate;
   }

   public void save(OrderBookAggregate aggregate, ImmutableList<DomainEvent> newEvents) {
       eventStore.save(aggregate.getId(), newEvents, aggregate.getVersion());

       // Every 100 events, create snapshot
       if ((aggregate.getVersion() + newEvents.size()) % 100 == 0) {
           snapshotRepo.saveSnapshot(aggregate.getId(), aggregate, aggregate.getVersion());
       }

       newEvents.forEach(eventDispatcher::dispatch);
   }
   ```

5. **Tests:**
   - Load with snapshot → verify correct state
   - Measure performance improvement

**Exit Criteria:**
- [ ] Snapshots created every 100 events
- [ ] Load time reduced significantly
- [ ] Tests verify correctness

---

### 🏅 Iteration 8 — Concurrency & Idempotency

**Goal:** Handle concurrent requests and duplicate submissions.

**Deliverables:**

1. **Optimistic Locking (Already Done):**
   - Event store enforces unique constraint on `(aggregate_id, version)`
   - On conflict → retry command

2. **Idempotency Keys:**
   ```java
   public record PlaceOrderCommand(
       OrderId orderId,             // Client-provided ID (UUID)
       UserId userId,
       Symbol getSymbol();
       Side getSide();
       BigDecimal getPrice();
       BigDecimal getQuantity();
       Optional<String> getIdempotencyKey();   // Optional

       @Override
       default AggregateId getAggregateId() {
           return AggregateId.of(getSymbol());
       }
   }
   ```

   - Store processed idempotency keys:
   ```sql
   CREATE TABLE idempotency_keys (
       key         VARCHAR(255) PRIMARY KEY,
       result      JSONB NOT NULL,
       created_at  TIMESTAMP NOT NULL DEFAULT NOW()
   );
   CREATE INDEX idx_idempotency_created ON idempotency_keys(created_at);
   ```

3. **Retry Logic:**
   ```java
   @Service
   public class OrderApplicationService {
       @Retryable(
           value = ConcurrencyException.class,
           maxAttempts = 3,
           backoff = @Backoff(delay = 100)
       )
       public OrderId placeOrder(PlaceOrderCommand command) {
           // Handle command with retry on version conflict
       }
   }
   ```

4. **Concurrency Tests:**
   ```java
   @Test
   void shouldHandleConcurrentOrders() throws Exception {
       Symbol symbol = Symbol.of("BTC-USD");

       // Submit 100 orders concurrently
       ExecutorService executor = Executors.newFixedThreadPool(10);
       List<Future<OrderId>> futures = new ArrayList<>();

       for (int i = 0; i < 100; i++) {
           futures.add(executor.submit(() ->
               orderService.placeOrder(createRandomOrder(symbol))
           ));
       }

       // Wait for all
       futures.forEach(f -> f.get());

       // Verify: order book consistent, all events stored
       List<DomainEvent> events = eventStore.loadEvents(symbol);
       assertThat(events).hasSize(100);
   }
   ```

5. **Rate Limiting (Optional):**
   - Use Resilience4j for rate limiting per user

**Exit Criteria:**
- [ ] Concurrent orders handled correctly
- [ ] No race conditions
- [ ] Idempotency works

---

### 🏅 Iteration 9 — Kafka Event Publishing

**Goal:** Publish domain events to Kafka for external systems.

**Deliverables:**

1. **Dependencies:**
   ```xml
   <dependency>
       <groupId>org.springframework.kafka</groupId>
       <artifactId>spring-kafka</artifactId>
   </dependency>
   ```

2. **Configuration:**
   ```yaml
   spring:
     kafka:
       bootstrap-servers: localhost:9092
       producer:
         key-serializer: org.apache.kafka.common.serialization.StringSerializer
         value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
   ```

3. **Event Publisher:**
   ```java
   @Component
   public class KafkaEventPublisher implements EventListener {
       private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

       @EventHandler
       public void on(TradeExecutedEvent event) {
           kafkaTemplate.send("trading.trades", event.symbol().value(), event);
       }

       @EventHandler
       public void on(OrderPlacedEvent event) {
           kafkaTemplate.send("trading.orders", event.symbol().value(), event);
       }
   }
   ```

4. **Topics:**
   - `trading.orders` — all order events
   - `trading.trades` — executed trades
   - `trading.positions` — position updates

5. **External Consumers (Demo):**
   - Create a separate consumer app
   - Listen to `trading.trades`
   - Log trades to console

6. **Tests:**
   - Use `@EmbeddedKafka` for testing
   - Verify events published correctly

**Exit Criteria:**
- [ ] Events published to Kafka
- [ ] External consumer receives events
- [ ] Integration tests pass

---

### 🏅 Iteration 10 — Advanced Order Types

**Goal:** Support market orders, stop-loss, FOK, IOC.

**Deliverables:**

1. **Order Types:**
   ```java
   public enum OrderType {
       LIMIT,          // Standard limit order
       MARKET,         // Execute at best available price
       STOP_LOSS,      // Trigger when price reaches stop price
       STOP_LIMIT,     // Combination
       FOK,            // Fill-or-kill (all or nothing)
       IOC             // Immediate-or-cancel (partial fill OK, cancel rest)
   }
   ```

2. **Market Order Logic:**
   - No price specified
   - Match against best available prices
   - Walk the order book until filled
   - Emit `MarketOrderExecuted` event

3. **Stop-Loss Orders:**
   - Store in separate "stop orders" map
   - Monitor trades
   - When trigger price hit → convert to market/limit order

4. **FOK/IOC Orders:**
   - Check if can fill immediately
   - FOK: if not fully fillable → reject
   - IOC: fill what's possible, cancel rest

5. **Updated Matching Engine:**
   ```java
   public List<DomainEvent> handle(PlaceOrderCommand cmd) {
       return switch (cmd.type()) {
           case LIMIT -> handleLimitOrder(cmd);
           case MARKET -> handleMarketOrder(cmd);
           case STOP_LOSS -> handleStopLossOrder(cmd);
           case FOK -> handleFOKOrder(cmd);
           case IOC -> handleIOCOrder(cmd);
       };
   }
   ```

6. **Tests:**
   - Market order execution
   - Stop-loss trigger
   - FOK rejection
   - IOC partial fill

**Exit Criteria:**
- [ ] All order types supported
- [ ] Matching logic updated
- [ ] Tests cover edge cases

---

## 🧪 Testing Strategy

### Unit Tests (Iteration 1-3)
- Pure domain logic
- No Spring context
- Fast (milliseconds)
- 100% coverage on matching logic

### Integration Tests (Iteration 4+)
- `@SpringBootTest`
- Testcontainers for PostgreSQL
- REST API tests
- End-to-end scenarios

### Property-Based Tests
- Use jqwik or QuickTheories
- Invariants:
  - Order book always sorted
  - Total quantity conserved
  - No negative balances

### Performance Tests
- Load testing with Gatling/JMeter
- Target: 1000 orders/sec per symbol
- Measure latency percentiles (p50, p95, p99)

---

## 📁 Final Project Structure

```
trading-engine/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/trading/
│   │   │       ├── domain/                   # Pure domain (no deps)
│   │   │       │   ├── model/
│   │   │       │   │   ├── Order.java
│   │   │       │   │   ├── Trade.java
│   │   │       │   │   ├── Side.java
│   │   │       │   │   ├── Symbol.java
│   │   │       │   │   └── ...
│   │   │       │   ├── orderbook/
│   │   │       │   │   ├── OrderBook.java
│   │   │       │   │   ├── OrderBookAggregate.java
│   │   │       │   │   └── MatchingEngine.java
│   │   │       │   ├── command/
│   │   │       │   │   ├── Command.java
│   │   │       │   │   ├── PlaceOrderCommand.java
│   │   │       │   │   └── CancelOrderCommand.java
│   │   │       │   ├── event/
│   │   │       │   │   ├── DomainEvent.java
│   │   │       │   │   ├── OrderPlacedEvent.java
│   │   │       │   │   ├── TradeExecutedEvent.java
│   │   │       │   │   └── ...
│   │   │       │   └── exception/
│   │   │       │       └── DomainException.java
│   │   │       ├── application/              # Application services
│   │   │       │   ├── OrderApplicationService.java
│   │   │       │   ├── OrderBookRepository.java
│   │   │       │   └── EventStore.java (interface)
│   │   │       └── infrastructure/           # Spring + external deps
│   │   │           ├── rest/
│   │   │           │   ├── OrderController.java
│   │   │           │   ├── QueryController.java
│   │   │           │   └── dto/
│   │   │           ├── persistence/
│   │   │           │   ├── PostgresEventStore.java
│   │   │           │   ├── SnapshotRepository.java
│   │   │           │   └── entity/
│   │   │           ├── projection/
│   │   │           │   ├── OrderBookProjection.java
│   │   │           │   ├── PositionProjection.java
│   │   │           │   └── TradeProjection.java
│   │   │           ├── messaging/
│   │   │           │   └── KafkaEventPublisher.java
│   │   │           └── config/
│   │   │               ├── EventStoreConfig.java
│   │   │               └── KafkaConfig.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           ├── V1__create_events_table.sql
│   │           ├── V2__create_projections.sql
│   │           └── V3__create_snapshots_table.sql
│   └── test/
│       └── java/
│           └── com/trading/
│               ├── domain/
│               │   └── OrderBookTest.java
│               ├── application/
│               │   └── OrderApplicationServiceTest.java
│               └── infrastructure/
│                   ├── PostgresEventStoreTest.java
│                   └── OrderControllerTest.java
├── docker-compose.yml                       # Postgres + Kafka
├── pom.xml (or build.gradle)
└── README.md
```

---

## 🔧 Local Development Setup

### Prerequisites
- Java 21
- Docker (for Postgres + Kafka)
- Maven/Gradle

### Docker Compose
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: trading_engine
      POSTGRES_USER: trading
      POSTGRES_PASSWORD: trading123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

volumes:
  postgres_data:
```

### Run
```bash
# Start infrastructure
docker-compose up -d

# Run application
./mvnw spring-boot:run

# Run tests
./mvnw test
```

---

## 📊 Success Metrics

By Iteration 10, you should have:

✅ **Functional Requirements:**
- Place limit/market/stop-loss orders
- Cancel orders
- Match orders (price-time priority)
- Query order book, trades, positions
- Replay system from events

✅ **Non-Functional Requirements:**
- < 10ms order placement latency (p95)
- 1000+ orders/sec throughput per symbol
- Zero data loss (event sourcing)
- Deterministic replay
- Full audit trail

✅ **Code Quality:**
- 80%+ test coverage
- Zero Spring in domain layer
- Immutable domain objects
- Clean architecture

---

## 🎓 Learning Outcomes

This project demonstrates:

1. **Event Sourcing** — storing facts, not state
2. **CQRS** — separate read/write concerns
3. **Domain-Driven Design** — aggregates, commands, events
4. **Concurrency** — optimistic locking, retries
5. **Performance** — snapshots, projections
6. **Testing** — unit, integration, property-based
7. **Infrastructure** — Postgres, Kafka, Flyway
8. **Clean Architecture** — dependency inversion

---

## 🚀 Start with Iteration 1

Begin with **pure matching logic**, no framework.

Next step:
```bash
mkdir -p src/main/java/com/trading/domain/model
mkdir -p src/test/java/com/trading/domain
```

Create `Order.java`, `OrderBook.java`, and start writing tests.

Good luck building your trading engine! 🚀