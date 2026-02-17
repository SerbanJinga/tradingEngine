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

Use Java records for all domain objects:

```java
public record Order(
    OrderId id,
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
    public Order withFilled(BigDecimal newFilled) {
        return new Order(id, userId, symbol, side, type, price,
                        quantity, newFilled, status, createdAt);
    }
}
```

**Why:**
- Thread-safe by default
- No accidental mutation
- Easy to reason about
- Excellent for concurrent systems

### ✅ 4. Aggregate Root = OrderBook per Symbol

**One aggregate per trading pair** (e.g., `BTC-USD`)

```java
public class OrderBookAggregate {
    private final Symbol symbol;
    private final NavigableMap<BigDecimal, List<Order>> bids;  // Descending
    private final NavigableMap<BigDecimal, List<Order>> asks;  // Ascending
    private int version;

    public List<DomainEvent> handle(PlaceOrderCommand cmd) {
        // 1. Validate
        // 2. Add to book
        // 3. Match
        // 4. Return events
    }
}
```

**Concurrency Strategy:**
- One aggregate instance per symbol loaded into memory
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
public interface Command {
    AggregateId aggregateId();
}

public interface DomainEvent {
    UUID eventId();
    AggregateId aggregateId();
    Instant occurredAt();
}

public interface EventStore {
    void save(AggregateId id, List<DomainEvent> events, int expectedVersion);
    List<DomainEvent> loadEvents(AggregateId id);
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
| Language | Java 21 | Records, pattern matching, virtual threads |
| Framework | Spring Boot 3.2+ | REST, DI (infrastructure only) |
| Database | PostgreSQL 16 | Event store + projections |
| Migration | Flyway | Schema versioning |
| Messaging | Kafka | External event publishing (Iteration 9) |
| Testing | JUnit 5, AssertJ, Testcontainers | Unit + integration tests |
| Build | Maven / Gradle | Dependency management |
| JSON | Jackson | Event serialization |

---

## 🚀 Iteration Plan

### 🥇 Iteration 1 — Pure Matching Engine (No Framework)

**Goal:** Build core domain logic with zero dependencies.

**Deliverables:**
1. **Domain Objects** (records):
   - `OrderId`, `UserId`, `Symbol`, `Side`, `OrderType`, `OrderStatus`
   - `Order`
   - `Trade`

2. **OrderBook Class:**
   ```java
   public class OrderBook {
       private final Symbol symbol;
       private final NavigableMap<BigDecimal, Queue<Order>> bids;
       private final NavigableMap<BigDecimal, Queue<Order>> asks;

       public MatchResult addOrder(Order order);
       public boolean cancelOrder(OrderId orderId);
       public List<Trade> match(Order incomingOrder);
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
       BigDecimal price,
       BigDecimal quantity
   ) implements Command {}

   public record CancelOrderCommand(
       OrderId orderId,
       UserId userId
   ) implements Command {}
   ```

2. **Domain Events:**
   ```java
   public record OrderPlacedEvent(
       UUID eventId,
       OrderId orderId,
       UserId userId,
       Symbol symbol,
       Side side,
       BigDecimal price,
       BigDecimal quantity,
       Instant occurredAt
   ) implements DomainEvent {}

   public record TradeExecutedEvent(
       UUID eventId,
       Symbol symbol,
       OrderId makerOrderId,
       OrderId takerOrderId,
       UserId makerId,
       UserId takerId,
       BigDecimal price,
       BigDecimal quantity,
       Instant occurredAt
   ) implements DomainEvent {}

   public record OrderCancelledEvent(...) implements DomainEvent {}
   public record OrderFilledEvent(...) implements DomainEvent {}
   ```

3. **OrderBookAggregate:**
   ```java
   public class OrderBookAggregate {
       private final Symbol symbol;
       private final OrderBook orderBook;
       private int version = 0;

       public List<DomainEvent> handle(PlaceOrderCommand cmd) {
           // Business logic + validation
           // Return events (don't mutate state yet)
       }

       public void apply(DomainEvent event) {
           // Apply event to internal state
           // Used during replay
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
                 List<DomainEvent> events,
                 int expectedVersion);
       List<DomainEvent> loadEvents(AggregateId aggregateId);
       List<DomainEvent> loadAllEvents();
   }
   ```

2. **In-Memory Implementation:**
   ```java
   public class InMemoryEventStore implements EventStore {
       private final Map<AggregateId, List<StoredEvent>> eventStreams;
       // Optimistic locking check on save()
   }
   ```

3. **Event Dispatcher:**
   ```java
   public class EventDispatcher {
       private final List<EventListener> listeners;

       public void dispatch(DomainEvent event) {
           listeners.forEach(l -> l.handle(event));
       }
   }
   ```

4. **Aggregate Repository:**
   ```java
   public class OrderBookRepository {
       private final EventStore eventStore;

       public OrderBookAggregate load(Symbol symbol) {
           List<DomainEvent> events = eventStore.loadEvents(symbol);
           OrderBookAggregate aggregate = new OrderBookAggregate(symbol);
           events.forEach(aggregate::apply);
           return aggregate;
       }

       public void save(OrderBookAggregate aggregate,
                       List<DomainEvent> newEvents) {
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
           List<DomainEvent> events = aggregate.handle(command);
           repository.save(aggregate, events);
           return command.orderId();
       }
   }
   ```

4. **DTOs (input/output):**
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
       public void save(AggregateId id, List<DomainEvent> events, int expectedVersion) {
           // 1. Check version (optimistic locking)
           // 2. Insert events with version = expectedVersion + 1, + 2, ...
           // 3. Handle unique constraint violation → throw ConcurrencyException
       }

       @Override
       public List<DomainEvent> loadEvents(AggregateId id) {
           // SELECT event_type, event_data FROM events
           // WHERE aggregate_id = ? ORDER BY version ASC
           // Deserialize JSON → DomainEvent
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
           fromVersion = snapshot.get().version();
       } else {
           aggregate = new OrderBookAggregate(symbol);
       }

       // 2. Load events since snapshot
       List<DomainEvent> events = eventStore.loadEventsSince(symbol, fromVersion);
       events.forEach(aggregate::apply);

       return aggregate;
   }

   public void save(OrderBookAggregate aggregate, List<DomainEvent> newEvents) {
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
       OrderId orderId,        // Client-provided ID (UUID)
       UserId userId,
       Symbol symbol,
       Side side,
       BigDecimal price,
       BigDecimal quantity,
       String idempotencyKey   // Optional
   ) implements Command {}
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