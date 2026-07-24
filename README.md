## Stock Price Tracker

An event-driven system for tracking asset prices and alerting users when a price
crosses a threshold they've defined. Prices are polled from an external source,
published onto Kafka, evaluated against user-defined rules, and (eventually) pushed
out as real-time notifications.

Currently ingests cryptocurrency prices from [CoinGecko](https://www.coingecko.com/en/api)
as the initial data source, with the pipeline designed to support other price feeds
(e.g. equities) in future.

### How it works

1. **`price-ingestion-service`** polls CoinGecko on a fixed interval for configured
   symbols and publishes `PriceTick` events to the `price-ticks` Kafka topic.
2. **`alert-rule-service`** exposes a REST API (`/api/alert-rules`) so users can
   create, list, toggle, and delete price alert rules (e.g. "notify me when BTC
   goes above $70,000"), backed by Postgres.
3. **`alert-evaluation-service`** consumes `price-ticks`, checks each tick against
   active alert rules with a cooldown window, and publishes `AlertTriggeredEvent`s
   to the `alert-triggers` topic.
4. **`notification-service`** consumes `alert-triggers`, persists notifications, and
   broadcasts them to connected clients over STOMP/WebSocket.

### Modules

| Module | Role |
|---|---|
| `price-ingestion-service` | Polls CoinGecko and publishes `PriceTick` events. |
| `alert-rule-service` | Postgres-backed CRUD API for user alert rules. |
| `alert-evaluation-service` | Evaluates price ticks against rules and raises triggered alerts. |
| `notification-service` | Delivers triggered alerts to users in real time. |
| `commons` | Shared DTOs/enums/constants (`PriceTick`, `AlertTriggeredEvent`, `AlertCondition`, `KafkaTopics`). |

### Stack

- Java 25, Spring Boot 4.1.0
- Spring Kafka, KRaft-mode Kafka (no Zookeeper)
- Postgres 16 with Flyway-managed schemas
- Lombok
- Testcontainers for integration tests against real Kafka/Postgres

### Running locally

```bash
# Build all modules
./mvnw package

# Start Kafka + Postgres + all four services (price-ingestion-service,
# alert-rule-service, alert-evaluation-service, notification-service) + kafka-ui
docker compose up
```

kafka-ui is available at http://localhost:8090 once the stack is running.

See `CLAUDE.md` for detailed build commands and architecture notes.
