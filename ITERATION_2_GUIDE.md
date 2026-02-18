# 🥈 Iteration 2 - Implementation Guide

**Application Layer with In-Memory Event Store**

---

## 🎯 Goal

Build the application layer with event sourcing infrastructure (still in-memory, no database).

**Duration:** 3-5 hours
**Module:** `application`
**Framework:** Zero framework dependencies (pure Java)
**Tests Required:** Integration tests for event store and aggregate replay

---

## 💡 What You're Building

In Iteration 1, you built the **domain layer** - pure business logic with no dependencies.

In Iteration 2, you're building the **application layer** - the orchestration layer that:
- Stores events in an event store (in-memory for now)
- Loads aggregates by replaying their event history
- Provides application services that handle commands
- Dispatches events to listeners (for read models in later iterations)
- Ensures optimistic locking for concurrent modifications

**Key Principle:** The domain layer remains pure. The application layer orchestrates it.

---

## 📋 Deliverables Checklist

- [ ] **Event Store Interface** - Contract for storing/loading events
- [ ] **InMemoryEventStore** - ConcurrentHashMap-based implementation
- [ ] **StoredEvent** - Wrapper with metadata (version, timestamp)
- [ ] **EventStore Tests** - Save, load, optimistic locking
- [ ] **OrderBookRepository** - Loads aggregates from events
- [ ] **Repository Tests** - Load, save, replay scenarios
- [ ] **OrderApplicationService** - Command handlers
- [ ] **Application Service Tests** - End-to-end command handling
- [ ] **EventDispatcher** (Optional) - Publish events to listeners
- [ ] **Integration Tests** - Full flow from command to persisted events

---

## 📂 Step 1: Create Application Module Structure

### 1.1 Create Module Directory

```bash
cd /Users/MihaiJinga/Downloads/tradingengine

# Create application module
mkdir -p application/src/main/java/com/trading/application
mkdir -p application/src/test/java/com/trading/application

# Create packages
mkdir -p application/src/main/java/com/trading/application/eventstore
mkdir -p application/src/main/java/com/trading/application/repository
mkdir -p application/src/main/java/com/trading/application/service
mkdir -p application/src/test/java/com/trading/application/eventstore
mkdir -p application/src/test/java/com/trading/application/repository
mkdir -p application/src/test/java/com/trading/application/service
```

### 1.2 Create pom.xml for Application Module

**File:** `application/pom.xml`

**Key points:**
- Parent: trading-engine-parent
- ArtifactId: application
- Depends on: domain module
- Dependencies: Guava (for ImmutableList), JUnit, AssertJ
- NO Spring or external frameworks yet

**Expected structure:**
```
application/
├── pom.xml
├── src/
│   ├── main/java/com/trading/application/
│   │   ├── eventstore/
│   │   ├── repository/
│   │   └── service/
│   └── test/java/com/trading/application/
│       ├── eventstore/
│       ├── repository/
│       └── service/
```

### 1.3 Update Parent POM

Add `<module>application</module>` to parent pom.xml modules section.

---

## 🗄️ Step 2: Design the Event Store

### 2.1 Understanding Event Sourcing

**Traditional approach:**
```
Command → Update Database → Current State
```

**Event Sourcing approach:**
```
Command → Emit Events → Store Events → Current State = Replay(All Events)
```

**Benefits:**
- Full audit trail
- Time travel (replay to any point)
- Event-driven architecture
- Easy to add new projections

### 2.2 EventStore Interface

**File:** `application/src/main/java/com/trading/application/eventstore/EventStore.java`

**Purpose:** Contract for storing and retrieving events

**Methods to define:**

1. `void save(AggregateId aggregateId, ImmutableList<DomainEvent> events, int expectedVersion)`
   - Saves multiple events atomically
   - `expectedVersion` for optimistic locking
   - Throws `ConcurrencyException` if version mismatch

2. `ImmutableList<DomainEvent> loadEvents(AggregateId aggregateId)`
   - Loads all events for a specific aggregate
   - Returns events in order (oldest first)
   - Returns empty list if aggregate doesn't exist

3. `ImmutableList<DomainEvent> loadAllEvents()`
   - Loads all events across all aggregates
   - Used for building read models
   - Returns events sorted by timestamp

**Design considerations:**
- All methods return Guava ImmutableList (immutability)
- Events are append-only (never modified or deleted)
- Version numbers start at 1 and increment

---

## 💾 Step 3: Implement InMemoryEventStore

### 3.1 StoredEvent Record

**File:** `application/src/main/java/com/trading/application/eventstore/StoredEvent.java`

**Purpose:** Wraps domain events with metadata

**Fields:**
- `DomainEvent event` - The actual domain event
- `int version` - Version number in the stream
- `Instant storedAt` - When it was stored

**Why?** Domain events don't know about storage concerns. StoredEvent adds that layer.

### 3.2 InMemoryEventStore Implementation

**File:** `application/src/main/java/com/trading/application/eventstore/InMemoryEventStore.java`

**Implementation strategy:**

1. **Data Structure:**
   ```
   ConcurrentHashMap<AggregateId, List<StoredEvent>>
   ```
   - Key: AggregateId (e.g., "BTC-USD" for OrderBook)
   - Value: Ordered list of StoredEvent (chronological)

2. **save() method:**
   - Lock on the aggregate's event stream
   - Check current version matches expectedVersion
   - If mismatch → throw `ConcurrencyException`
   - Append events with incremental version numbers
   - Store with timestamp

3. **loadEvents() method:**
   - Get list from map (or empty list)
   - Extract DomainEvent from each StoredEvent
   - Return as ImmutableList

4. **loadAllEvents() method:**
   - Flatten all event streams
   - Sort by storedAt timestamp
   - Return as ImmutableList

**Concurrency considerations:**
- Use `synchronized` on aggregate stream for save()
- ConcurrentHashMap for map-level operations
- Defensive copies when returning data

### 3.3 ConcurrencyException

**File:** `application/src/main/java/com/trading/application/eventstore/ConcurrencyException.java`

**Purpose:** Thrown when optimistic locking fails

**Extends:** RuntimeException

**Fields:**
- `AggregateId aggregateId`
- `int expectedVersion`
- `int actualVersion`

---

## 🧪 Step 4: Test EventStore

### 4.1 InMemoryEventStoreTest

**File:** `application/src/test/java/com/trading/application/eventstore/InMemoryEventStoreTest.java`

**Test scenarios:**

1. **shouldStartEmpty**
   - Load events for non-existent aggregate
   - Verify empty list returned

2. **shouldSaveAndLoadSingleEvent**
   - Save one event
   - Load events
   - Verify event is returned

3. **shouldSaveAndLoadMultipleEvents**
   - Save multiple events in one call
   - Verify order preserved
   - Verify all returned

4. **shouldIncrementVersionNumbers**
   - Save events with expectedVersion=0
   - Save more events with expectedVersion=2
   - Verify versions are 1, 2, 3, 4...

5. **shouldThrowConcurrencyExceptionOnVersionMismatch**
   - Save events at version 0
   - Try to save at version 0 again (should fail)
   - Verify ConcurrencyException thrown

6. **shouldLoadAllEventsAcrossAggregates**
   - Save events for multiple aggregates
   - Load all events
   - Verify all returned in chronological order

7. **shouldHandleConcurrentSavesCorrectly**
   - Create multiple threads
   - Each tries to append to same aggregate
   - Only one should succeed per version
   - Others should get ConcurrencyException

**Testing tips:**
- Use test helper methods to create events
- Use AssertJ for fluent assertions
- Test thread safety explicitly

---

## 📦 Step 5: Build Aggregate Repository

### 5.1 OrderBookRepository

**File:** `application/src/main/java/com/trading/application/repository/OrderBookRepository.java`

**Purpose:** Load and save OrderBook aggregates using event sourcing

**Dependencies:**
- EventStore
- (Optional) EventDispatcher

**Key methods:**

1. `OrderBookAggregate load(Symbol symbol)`
   - Create AggregateId from symbol
   - Load events from EventStore
   - Create new OrderBookAggregate
   - Replay all events via aggregate.apply()
   - Return hydrated aggregate

2. `void save(OrderBookAggregate aggregate)`
   - Get uncommitted events from aggregate
   - Save to EventStore with current version
   - Dispatch events (optional)
   - Mark events as committed on aggregate

**Design pattern:** Repository pattern separates domain from infrastructure

### 5.2 Event Replay

**How it works:**

```
1. EventStore returns: [Event1, Event2, Event3]
2. Create empty aggregate
3. aggregate.apply(Event1) → updates internal state
4. aggregate.apply(Event2) → updates internal state
5. aggregate.apply(Event3) → updates internal state
6. Return aggregate with reconstructed state
```

**Why this works:**
- Events are immutable facts
- Replaying events is deterministic
- State is derived from events, not stored directly

---

## 🧪 Step 6: Test Repository

### 6.1 OrderBookRepositoryTest

**File:** `application/src/test/java/com/trading/application/repository/OrderBookRepositoryTest.java`

**Test scenarios:**

1. **shouldLoadNewAggregate**
   - Load aggregate that doesn't exist yet
   - Verify aggregate is empty (version 0)

2. **shouldSaveAndLoadAggregate**
   - Create aggregate
   - Handle command (produces events)
   - Save aggregate
   - Load aggregate again
   - Verify state is reconstructed correctly

3. **shouldReplayEventsInOrder**
   - Place multiple orders
   - Save aggregate
   - Load aggregate
   - Verify order book state matches expected

4. **shouldIncrementVersionOnEachSave**
   - Save aggregate (version 1)
   - Handle another command
   - Save again (version 2)
   - Verify version increments

5. **shouldThrowConcurrencyExceptionOnConflict**
   - Load aggregate twice (same version)
   - Modify both copies
   - Save first copy (succeeds)
   - Save second copy (fails with ConcurrencyException)

6. **shouldClearUncommittedEventsAfterSave**
   - Handle command
   - Verify uncommitted events exist
   - Save
   - Verify uncommitted events cleared

**Integration test level:** These tests exercise EventStore + Repository + Aggregate together

---

## 🎯 Step 7: Build Application Service

### 7.1 OrderApplicationService

**File:** `application/src/main/java/com/trading/application/service/OrderApplicationService.java`

**Purpose:** High-level command handlers that orchestrate the flow

**Dependencies:**
- OrderBookRepository

**Methods to implement:**

1. `OrderId placeOrder(PlaceOrderCommand command)`
   - Load OrderBookAggregate for the symbol
   - Handle command (produces events)
   - Save aggregate (persists events)
   - Return orderId

2. `void cancelOrder(CancelOrderCommand command)`
   - Load OrderBookAggregate for the symbol
   - Handle command (produces events)
   - Save aggregate

**Transaction boundary:** Each method is a transaction (load → command → save)

**Error handling:**
- Domain exceptions bubble up (validation errors)
- ConcurrencyException → retry logic or fail
- Use descriptive exceptions

### 7.2 Command Handling Flow

```
1. Client calls: placeOrder(command)
2. Load aggregate from repository (replays events)
3. Call: aggregate.handle(command)
4. Aggregate validates business rules
5. Aggregate returns events (OrderPlaced, TradeExecuted, etc.)
6. Repository saves events to EventStore
7. Events dispatched to listeners (optional)
8. Return result to client
```

**Key insight:** Application service is thin orchestration. Business logic stays in domain.

---

## 🧪 Step 8: Test Application Service

### 8.1 OrderApplicationServiceTest

**File:** `application/src/test/java/com/trading/application/service/OrderApplicationServiceTest.java`

**Test scenarios:**

1. **shouldPlaceOrderSuccessfully**
   - Create command
   - Call placeOrder()
   - Verify events stored in EventStore
   - Load aggregate and verify order in book

2. **shouldMatchOrdersAutomatically**
   - Place SELL order
   - Place BUY order at crossing price
   - Verify both orders filled
   - Verify TradeExecuted event stored

3. **shouldCancelOrderSuccessfully**
   - Place order
   - Cancel order
   - Verify order removed from book
   - Verify OrderCancelled event stored

4. **shouldHandlePartialFills**
   - Place large SELL order
   - Place small BUY order
   - Verify partial fill
   - Place another BUY to fill remainder
   - Verify fully filled

5. **shouldRejectInvalidCommands**
   - Try to place order with negative price
   - Verify exception thrown
   - Verify no events stored

6. **shouldHandleConcurrentOrderPlacement**
   - Load same aggregate in two threads
   - Place different orders simultaneously
   - One should succeed, one should retry
   - Both orders should eventually be in book

**End-to-end testing:** These tests verify the entire application layer works together

---

## 🔔 Step 9: Event Dispatcher (Optional)

### 9.1 Purpose

**Why?** In later iterations, you'll need read models (projections). EventDispatcher notifies listeners when events happen.

**Example use case:**
```
TradeExecuted event → Update TradesView (denormalized table)
OrderPlaced event → Update UserOrdersView
```

### 9.2 EventListener Interface

**File:** `application/src/main/java/com/trading/application/eventstore/EventListener.java`

**Single method:**
- `void handle(DomainEvent event)`

**Implementations** (in later iterations):
- TradeProjector
- OrderBookProjector
- PositionProjector

### 9.3 EventDispatcher Implementation

**File:** `application/src/main/java/com/trading/application/eventstore/EventDispatcher.java`

**Constructor:**
- Takes ImmutableList<EventListener>

**Method:**
- `void dispatch(DomainEvent event)`
- Calls each listener's handle() method

**Error handling:**
- Catch exceptions from listeners
- Log but don't fail the transaction
- Continue dispatching to other listeners

**Note:** Keep it simple for now. In Iteration 5+ you'll add async publishing.

---

## 🏗️ Step 10: Integration Tests

### 10.1 End-to-End Scenarios

**File:** `application/src/test/java/com/trading/application/OrderBookIntegrationTest.java`

**Test full workflows:**

1. **shouldHandleCompleteOrderLifecycle**
   - Place order
   - Partially fill it
   - Fully fill it
   - Verify all events in correct order
   - Replay from scratch → verify same state

2. **shouldMatchMultipleOrders**
   - Place 3 SELL orders at different prices
   - Place 1 BUY order that crosses all
   - Verify all trades
   - Verify maker price execution

3. **shouldReplayEventsCorrectly**
   - Execute complex scenario (10+ commands)
   - Load aggregate fresh (replay all events)
   - Verify state matches
   - Verify deterministic replay

4. **shouldHandleEventStoreReset**
   - Place orders
   - Clear event store
   - Place new orders
   - Verify clean start

5. **shouldSupportTimeTravel**
   - Execute scenario
   - Replay only first N events
   - Verify state at that point in time
   - Demonstrates event sourcing power

---

## 📁 Final Module Structure

```
application/
├── pom.xml
├── src/
│   ├── main/java/com/trading/application/
│   │   ├── eventstore/
│   │   │   ├── EventStore.java                    (interface)
│   │   │   ├── InMemoryEventStore.java            (implementation)
│   │   │   ├── StoredEvent.java                   (record)
│   │   │   ├── ConcurrencyException.java          (exception)
│   │   │   ├── EventDispatcher.java               (optional)
│   │   │   └── EventListener.java                 (optional interface)
│   │   ├── repository/
│   │   │   └── OrderBookRepository.java           (aggregate repository)
│   │   └── service/
│   │       └── OrderApplicationService.java       (command handlers)
│   └── test/java/com/trading/application/
│       ├── eventstore/
│       │   └── InMemoryEventStoreTest.java        (unit tests)
│       ├── repository/
│       │   └── OrderBookRepositoryTest.java       (integration tests)
│       ├── service/
│       │   └── OrderApplicationServiceTest.java   (integration tests)
│       └── OrderBookIntegrationTest.java          (end-to-end tests)
```

---

## ✅ Success Criteria

Before moving to Iteration 3, verify:

- [ ] **EventStore works correctly**
  - Saves and loads events
  - Optimistic locking prevents conflicts
  - All tests passing

- [ ] **Repository reconstructs aggregates**
  - Replay produces correct state
  - Version tracking works
  - Uncommitted events managed properly

- [ ] **Application service handles commands**
  - PlaceOrder works end-to-end
  - CancelOrder works end-to-end
  - Events persisted correctly

- [ ] **Tests are comprehensive**
  - Unit tests for EventStore
  - Integration tests for Repository
  - End-to-end tests for full flows
  - Concurrency tests pass

- [ ] **Code quality**
  - No framework dependencies in application layer
  - Clean separation of concerns
  - Immutable data structures (Guava ImmutableList)
  - Proper exception handling

---

## 🎓 Key Concepts to Understand

### Event Sourcing Benefits
- **Audit Trail:** Every state change is recorded
- **Time Travel:** Replay to any point in time
- **Debugging:** Reproduce exact sequence of events
- **Event-Driven:** Easy to add projections later

### Optimistic Locking
- **Why:** Allow concurrent reads, detect concurrent writes
- **How:** Version numbers on each save
- **Alternative:** Pessimistic locking (slower, simpler)

### Repository Pattern
- **Purpose:** Abstract persistence from domain
- **Benefit:** Domain doesn't know about event store
- **Trade-off:** Extra layer, but worth it for testing

### CQRS Foundation
- **Commands:** PlaceOrder, CancelOrder (write side)
- **Events:** OrderPlaced, TradeExecuted (facts)
- **Queries:** Coming in Iteration 3 (read side)

---

## 🚀 Next Steps

After completing Iteration 2:

**Iteration 3:** Add PostgreSQL persistence
- Replace InMemoryEventStore with JdbcEventStore
- Add Flyway migrations
- Test with real database

**Iteration 4:** Add REST API
- Spring Boot integration
- REST controllers
- Request/Response DTOs

**Iteration 5:** Add read models (CQRS query side)
- Projections from events
- Denormalized views
- Fast queries

---

## 💡 Tips & Tricks

### Testing Tips
- Write tests first (TDD approach)
- Test unhappy paths (concurrency, invalid input)
- Use descriptive test names
- One assertion per test (usually)

### Design Tips
- Keep application service thin
- Business logic stays in domain
- Use interfaces for testability
- Return immutable collections

### Debugging Tips
- Log all events when saved
- Print aggregate state after replay
- Use debugger to step through replay
- Check version numbers on conflicts

### Performance Tips
- ConcurrentHashMap for thread safety
- Don't load all events if not needed
- Consider snapshots (later iteration)
- Profile before optimizing

---

## 📚 Further Reading

- Martin Fowler: Event Sourcing
- Greg Young: CQRS Documents
- Vaughn Vernon: Implementing Domain-Driven Design
- Microsoft: CQRS Journey

---

**Good luck! 🚀**

*Remember: Event sourcing is a mindset shift. Events are the source of truth, not current state.*
