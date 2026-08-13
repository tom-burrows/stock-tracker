## Architecture

Event-driven microservices system for stock price alerts. Services communicate via Kafka topics; Postgres stores alert rules.

| Module | Role |
|---|---|
| `price-ingestion-service` | Polls CoinGecko on a schedule and publishes `PriceTick` events to the `price-ticks` topic. |
| `alert-rule-service` | Postgres-backed CRUD REST API (`/api/alert-rules`) for user-defined alert rules; owns the `alert_rules` schema via Flyway. |
| `alert-evaluation-service` | Consumes `price-ticks` (`PriceTickListener`), evaluates `PRICE_ABOVE`/`PRICE_BELOW` rules with a configurable cooldown (`EvaluationProperties`, default 15m), publishes `AlertTriggeredEvent` to `alert-triggers`. |
| `notification-service` | Consumes `alert-triggers`, persists notifications (Flyway-managed `notifications` table), exposes `GET /api/notifications?userId=`, broadcasts via STOMP/WebSocket on `/topic/notifications`. |
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
- **`frontend/`**: Next.js (App Router, TypeScript, React 19, Tailwind v4), scaffolded via `create-next-app` — not Vite. `frontend/plan.md` records the architecture: Next.js Route Handlers act as a thin server-side REST proxy to `alert-rule-service` (`:8081`) and `notification-service` (`:8082`) so the browser only ever talks to the Next.js origin for REST (no CORS needed on `alert-rule-service`); the one exception is the STOMP/WebSocket connection to `notification-service`'s `/ws`, which the browser connects to *directly* (Route Handlers can't proxy WS upgrades on the standard Node runtime), so `notification-service` needs CORS configured for the Next.js dev origin (`http://localhost:3000`) for that handshake. `WebSocketConfig` has been updated to drop `.withSockJS()` in favor of plain STOMP-over-WS, with `setAllowedOrigins("http://localhost:3000")` for the cross-origin handshake (`NotificationIntegrationTest` updated to match: `ws://` scheme, plain `StandardWebSocketClient` instead of `SockJsClient`). The Next.js side is still just the unmodified `create-next-app` scaffold (`app/page.tsx` is the default starter page) — the Route Handler proxies and actual UI from `plan.md`'s "First implementation steps" haven't been built yet. Deliberately kept outside the Maven reactor (not in root `pom.xml` `<modules>`); runs via `npm run dev` (port 3000), not containerized. `frontend/CLAUDE.md` is `@AGENTS.md` — its `AGENTS.md` warns that this Next.js version has breaking changes vs. training data and to check `node_modules/next/dist/docs/` before writing frontend code.


### Stack

- Spring Boot 4.1.0, Java 25
- Spring Kafka (version managed by the Spring Boot BOM, currently 4.1.0)
- Lombok (annotation processor configured in root `pluginManagement`)
- Postgres 16, Kafka 3.7.0 (KRaft)
- Flyway for schema management on services that own a table (`alert-rule-service`, `notification-service`) — Hibernate `ddl-auto` is set to `validate`, never `update`
- Testcontainers 1.21.4 for integration tests (real Kafka + Postgres, not mocks/H2)