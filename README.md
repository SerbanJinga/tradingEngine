# Trading Engine

**Event-Sourced Trading Engine with CQRS**

A production-grade matching engine built with event sourcing, CQRS, and clean architecture principles.

## 🏗️ Architecture

### Multi-Module Structure

```
trading-engine/
├── domain/           # Pure domain model (zero framework dependencies)
├── application/      # Application services and use cases
├── infrastructure/   # Postgres, Kafka, projections
├── rest-api/         # REST controllers and DTOs
├── client/          # Java client library
└── webapp/          # Main Spring Boot application
```

### Key Architectural Decisions

- **Event Sourcing**: All state changes stored as immutable events
- **CQRS**: Separate write (commands) and read (projections) models
- **Immutability**: Using Immutables library + Guava collections
- **Clean Architecture**: Domain has zero dependencies on frameworks
- **Aggregate per Symbol**: One order book aggregate per trading pair

## 🚀 Quick Start

### Prerequisites

- Java 21
- Maven 3.8+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Kafka (port 9092)
- Zookeeper (port 2181)

### 2. Build the Project

```bash
./mvnw clean install
```

### 3. Run the Application

```bash
./mvnw spring-boot:run -pl webapp
```

The application will start on `http://localhost:8080`

### 4. Verify Health

```bash
curl http://localhost:8080/actuator/health
```

## 📦 Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Language | Java 21 | Pattern matching, virtual threads |
| Immutability | Immutables + Guava | Generate immutable objects |
| Framework | Spring Boot 3.2+ | REST, DI (infrastructure only) |
| Database | PostgreSQL 16 | Event store + projections |
| Migration | Flyway | Schema versioning |
| Messaging | Kafka | Event publishing |
| Testing | JUnit 5, Testcontainers | Unit + integration tests |
| Build | Maven | Multi-module management |

## 🎯 Implementation Progress

### ✅ Completed Iterations

- [ ] **Iteration 1**: Pure matching engine (no framework)
- [ ] **Iteration 2**: Commands & events
- [ ] **Iteration 3**: In-memory event store
- [ ] **Iteration 4**: Spring Boot integration
- [ ] **Iteration 5**: PostgreSQL + Flyway
- [ ] **Iteration 6**: CQRS projections
- [ ] **Iteration 7**: Snapshots
- [ ] **Iteration 8**: Concurrency & idempotency
- [ ] **Iteration 9**: Kafka event publishing
- [ ] **Iteration 10**: Advanced order types

See [TRADING_ENGINE_SPEC.md](TRADING_ENGINE_SPEC.md) for detailed iteration plans.

## 🧪 Testing

### Run All Tests

```bash
./mvnw test
```

### Run Tests for Specific Module

```bash
./mvnw test -pl domain
./mvnw test -pl infrastructure
```

### Integration Tests (with Testcontainers)

```bash
./mvnw verify -pl infrastructure
```

## 📂 Module Details

### Domain Module

**Pure Java, zero dependencies**

Contains:
- Immutable value objects (Order, Trade, Symbol, etc.)
- Commands (PlaceOrderCommand, CancelOrderCommand)
- Domain events (OrderPlacedEvent, TradeExecutedEvent)
- OrderBook matching logic

```bash
cd domain
../mvnw test
```

### Application Module

**Depends on: domain**

Contains:
- Application services (OrderApplicationService)
- Port interfaces (EventStore, Repository)
- Use case orchestration

### Infrastructure Module

**Depends on: domain, application**

Contains:
- PostgreSQL event store implementation
- CQRS projections (OrderBookProjection, PositionProjection)
- Kafka event publisher
- Flyway migrations

### REST API Module

**Depends on: domain, application**

Contains:
- REST controllers (OrderController, QueryController)
- Request/Response DTOs
- Validation

### Client Module

**Standalone Java client library**

Contains:
- TradingEngineClient (HTTP client)
- Client-side DTOs
- Minimal dependencies (can be used in other projects)

### Webapp Module

**Main application - depends on all modules**

Contains:
- Spring Boot application entry point
- Configuration
- Wires all modules together

## 🔧 Development

### Code Generation (Immutables)

Immutables are generated at compile time. Your IDE should auto-generate them, but if not:

```bash
./mvnw clean compile
```

Generated classes appear in `target/generated-sources/annotations/`

### Database Migrations

Located in: `infrastructure/src/main/resources/db/migration/`

Flyway runs automatically on startup. To manually migrate:

```bash
./mvnw flyway:migrate -pl infrastructure
```

### Clean Build

```bash
./mvnw clean install
```

## 📊 API Examples

### Place an Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTC-USD",
    "side": "BUY",
    "price": 50000.00,
    "quantity": 1.5
  }'
```

### Get Order Book

```bash
curl http://localhost:8080/api/query/orderbook/BTC-USD
```

### Get Recent Trades

```bash
curl http://localhost:8080/api/query/trades/BTC-USD
```

## 🐛 Troubleshooting

### PostgreSQL Connection Issues

```bash
# Check if Postgres is running
docker ps | grep postgres

# View logs
docker logs trading-engine-postgres

# Restart
docker-compose restart postgres
```

### Build Issues

```bash
# Clean everything
./mvnw clean
rm -rf ~/.m2/repository/com/trading

# Rebuild
./mvnw install
```

### IDE Not Recognizing Generated Classes

1. Run `./mvnw clean compile`
2. Refresh your IDE (IntelliJ: Cmd+Shift+A → "Reload All Maven Projects")
3. Mark `target/generated-sources/annotations` as "Generated Sources Root"

## 📖 Documentation

- [Implementation Spec](TRADING_ENGINE_SPEC.md) - Detailed architecture and iteration plans
- [API Documentation](docs/API.md) - REST API reference (TODO)
- [Event Catalog](docs/EVENTS.md) - Domain events reference (TODO)

## 🤝 Contributing

This is a side project for learning purposes. Feel free to explore and extend!

## 📝 License

This project is for educational purposes.

---

**Next Steps**: Start with Iteration 1 - implement the pure matching engine in the `domain` module.
