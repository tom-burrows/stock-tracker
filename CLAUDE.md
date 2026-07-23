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

# First time only: create the local env file docker-compose.yml reads Postgres credentials from
cp .env.example .env

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
| `price-ingestion-service` | Polls CoinGecko on a schedule and publishes `PriceTick` events to the `price-ticks` topic. Only service with a Dockerfile so far. |
| `alert-rule-service` | Postgres-backed CRUD REST API (`/api/alert-rules`) for user-defined alert rules; owns the `alert_rules` schema via Flyway. |
| `alert-evaluation-service` | Consumes `price-ticks`, evaluates against active rules with a cooldown window, publishes `AlertTriggeredEvent` to `alert-triggers` (not yet implemented). |
| `notification-service` | Consumes `alert-triggers`, persists notifications, broadcasts via STOMP/WebSocket (not yet implemented). |
| `commons` | Pure DTO/enum/constants library — `PriceTick`, `AlertTriggeredEvent`, `AlertCondition`, `KafkaTopics`. No Spring Boot app, no JPA, no Kafka client dependency of its own. |

`AlertRule` is deliberately **not** in `commons` — `alert-rule-service` (writer) and `alert-evaluation-service` (reader) each own their own JPA mapping to the same physical `alert_rules` table, so the two services stay independently deployable.

### Key wiring details

- **Kafka**: KRaft mode (no Zookeeper). Internal broker address is `kafka:19092`; host access is `localhost:9092`. Services inside Docker must use `kafka:19092` (set via `SPRING_KAFKA_BOOTSTRAP_SERVERS`).
- **commons dependency**: `commons` is a local Maven artifact (`tom.burrows:commons:0.0.1-SNAPSHOT`). It must be built (or installed to `~/.m2`) before modules that depend on it. The `-am` flag handles this automatically.
- **Docker context**: `docker-compose.yml` uses `context: .` (repo root) so all `COPY` paths in `price-ingestion-service/Dockerfile` are relative to the root, not the module directory.
- **Parent POM**: Each module's `<parent>` must point to `tom.burrows:stock-prices` (the aggregator), not directly to `spring-boot-starter-parent`, for `pluginManagement`/`dependencyManagement` to propagate correctly.
- **Dockerfile dependency pre-fetch**: `price-ingestion-service/Dockerfile` copies only POMs before running `mvn -pl price-ingestion-service -am dependency:go-offline` for layer caching. This can fail against reactor-internal dependencies (like `commons`) if no source exists yet to satisfy the reactor — if you hit this, copy module source before `go-offline`/`package`, accepting a less optimal cache layer (see `notes/debugging/docker-building-troubleshooting.md`).
- **Root pom `<dependencies>` is inherited, not just aggregated**: anything listed there becomes a real dependency of every child module. Keep it minimal (currently just `spring-boot-starter-test`) — each module should declare its own starters explicitly, or you'll silently force things like `spring-boot-starter-web` onto modules that shouldn't have it (e.g. `commons`, `alert-evaluation-service`).
- **Postgres credentials**: sourced from `.env` (gitignored, copy from `.env.example`) — `docker-compose.yml` auto-loads it for `${POSTGRES_USER}`/`${POSTGRES_PASSWORD}`/`${POSTGRES_DB}` substitution. Each service's own `application.yml` reads the same var names via `${POSTGRES_USER:alerts}`-style placeholders (defaulting to the same local dev values) for non-Docker runs, so the two paths stay in sync without duplicating literal credentials.
- **Flyway on a shared database**: `alert-rule-service` and `notification-service` both own Flyway migrations against the *same* `price_alerts` database (each owns a different table), and both happen to be named `V1__...`. Flyway's schema-history bookkeeping is scoped per database schema, not per service, so without `spring.flyway.table` set to a distinct name per service, the second one to start finds the other's "version 1" already recorded and fails checksum validation against its own different migration. A distinct table name alone isn't sufficient either — Flyway also refuses to touch a schema it finds non-empty (the other service's table already exists) unless `baseline-on-migrate: true` is set; `baseline-version: 0` (below any real migration) keeps that baseline from accidentally skipping the service's own real V1. This only ever surfaces when both services share one real (or real-shaped, e.g. `docker compose up`) database — the per-service Testcontainers integration tests each get a fully isolated database and can't catch it.

### Stack

- Spring Boot 4.1.0, Java 25
- Spring Kafka (version managed by the Spring Boot BOM, currently 4.1.0)
- Lombok (annotation processor configured in root `pluginManagement`)
- Postgres 16, Kafka 3.7.0 (KRaft)
- Flyway for schema management on services that own a table (`alert-rule-service`, and `notification-service` once implemented) — Hibernate `ddl-auto` is set to `validate`, never `update`
- Testcontainers 1.21.4 for integration tests (real Kafka + Postgres, not mocks/H2)

## Gotchas — Spring Boot 4.1.0 module splitting

Boot 4.1 split `spring-boot-autoconfigure` and `spring-boot-starter-test` into many feature-specific artifacts. A starter being on the classpath no longer guarantees its autoconfiguration fires — a second, non-obvious module is often needed too. These cost real debugging time to find; check this list before assuming something is a code bug.

- **`spring-kafka` alone does not autoconfigure anything.** `KafkaProperties`/`KafkaAutoConfiguration` live in `org.springframework.boot:spring-boot-kafka`. Without it there's no autoconfigured `KafkaTemplate`/`KafkaAdmin`/consumer factory at all — not a wrong-type issue, a missing bean entirely. Every module that uses `spring-kafka` needs this too.
- **`flyway-core` alone does not run migrations.** `FlywayAutoConfiguration` lives in `org.springframework.boot:spring-boot-flyway`. Add it explicitly on every service that owns Flyway-managed schema.
- **Boot's autoconfigured `KafkaTemplate` bean is wildcard-typed** (`KafkaTemplate<?, ?>`), so it won't satisfy constructor injection of a concretely-typed `KafkaTemplate<String, MyType>`. Build your own `ProducerFactory<String, MyType>` + `KafkaTemplate<String, MyType>` from the autoconfigured `KafkaProperties` instead (see `price-ingestion-service`'s `KafkaProducerConfig`).
- **Do not use `spring-boot-starter-test-classic`** (the "restore old bundled test annotations" umbrella) — it pulls in `spring-boot-grpc-test` and `spring-boot-security-test` without their corresponding production modules, breaking context loading with `ClassNotFoundException`s on unrelated classes (`SecurityAutoConfiguration`, `GrpcServerStartedEvent`) even in apps using neither gRPC nor Security. Depend on the specific granular test-slice modules instead: `spring-boot-webmvc-test` (`@WebMvcTest`, package `org.springframework.boot.webmvc.test.autoconfigure`), `spring-boot-data-jpa-test` (`@DataJpaTest`, package `org.springframework.boot.data.jpa.test.autoconfigure`), `spring-boot-jdbc-test` (`@AutoConfigureTestDatabase`, package `org.springframework.boot.jdbc.test.autoconfigure`), `spring-boot-resttestclient` + `spring-boot-restclient` (`TestRestTemplate`, package `org.springframework.boot.resttestclient`).
- **`@SpringBootTest(webEnvironment = RANDOM_PORT)` no longer auto-registers a `TestRestTemplate` bean.** Add `@AutoConfigureTestRestTemplate` (`org.springframework.boot.resttestclient.autoconfigure`) on the test class explicitly.
- **Boot 4.1's autoconfigured Jackson `ObjectMapper` bean is Jackson 3** (`tools.jackson.databind.ObjectMapper`), not the Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper` used elsewhere in this codebase (`commons`' DTOs, most test code). `commons` deliberately keeps a direct Jackson 2 dependency for its own standalone round-trip tests, and Spring Kafka's `JsonSerializer`/`JsonDeserializer` default to constructing their own Jackson 2 `ObjectMapper` internally, so the Kafka pipeline is unaffected. The gap only bites in tests that `@Autowire` Spring's own `ObjectMapper` bean expecting the Jackson 2 type (e.g. `@WebMvcTest` controller tests) — it'll fail to autowire since the bean is a different class entirely. Fix: construct `new ObjectMapper()` locally in the test instead of autowiring.
- **Maven Surefire's default include pattern does not match `*IT.java`** (that's Failsafe's convention; this project has no Failsafe plugin configured). A test class named e.g. `FooRepositoryIT` silently never runs under `./mvnw test`/`package`. Name integration test classes `*IntegrationTest` or `*Test`.
- **Testcontainers 1.20.4 cannot talk to recent Docker Desktop versions** (confirmed against 4.61.0 / API 1.53) — container-based tests fail at startup with a `BadRequestException`/empty-body 400 from the daemon. The root pom pins the Testcontainers BOM to 1.21.4 for this reason; don't downgrade it.
