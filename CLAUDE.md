# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

This is a Maven multi-module project. All commands should be run from the repo root.

```bash
# Build all modules
./mvnw package

# Build a single module (and its dependencies, e.g. commons)
./mvnw -pl price-ingestion-service -am package

# Run tests for all modules
./mvnw test

# Run tests for a single module
./mvnw -pl price-ingestion-service test

# Start infrastructure (Kafka + Postgres) and the price-ingestion-service container
docker compose up

# Build a Docker image with full output (useful for diagnosing Maven errors inside Docker)
docker compose build --no-cache --progress=plain price-ingestion-service 2>&1 | tee build.log
```

kafka-ui is available at http://localhost:8090 when the stack is running.

## Architecture

Event-driven microservices system for stock price alerts. Services communicate via Kafka topics; Postgres stores alert rules.

| Module | Role |
|---|---|
| `price-ingestion-service` | Ingests price data; publishes `Event` messages to Kafka. Only service with a Dockerfile so far. |
| `alert-rule-service` | Manages user-defined alert rules (stub). |
| `alert-evaluation-service` | Consumes prices and evaluates them against rules (stub). |
| `notification-service` | Sends notifications when alerts fire (stub). |
| `commons` | Shared library — currently provides the `Event` model and Lombok. |

### Key wiring details

- **Kafka**: KRaft mode (no Zookeeper). Internal broker address is `kafka:19092`; host access is `localhost:9092`. Services inside Docker must use `kafka:19092` (set via `SPRING_KAFKA_BOOTSTRAP_SERVERS`).
- **commons dependency**: `commons` is a local Maven artifact (`tom.burrows:commons:0.0.1-SNAPSHOT`). It must be built (or installed to `~/.m2`) before modules that depend on it. The `-am` flag handles this automatically.
- **Docker context**: `docker-compose.yml` uses `context: .` (repo root) so all `COPY` paths in `price-ingestion-service/Dockerfile` are relative to the root, not the module directory.
- **Parent POM**: Each module's `<parent>` must point to `tom.burrows:stock-prices` (the aggregator), not directly to `spring-boot-starter-parent`, for `pluginManagement`/`dependencyManagement` to propagate correctly.

### Stack

- Spring Boot 4.1.0, Java 25
- Spring Kafka (version managed by the Spring Boot BOM, currently 4.1.0)
- Lombok (annotation processor configured in root `pluginManagement`)
- Postgres 16, Kafka 3.7.0 (KRaft)
