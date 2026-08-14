# Backend Overview — for New Starters

This is a walkthrough of the Java side of Stock-Prices: what each service does, how they talk
to each other, and — the part you actually need — which endpoints the frontend calls and why.
It assumes you already know your way around the Next.js app; it doesn't re-explain that side.

## The shape of the system

It's an event-driven set of four Spring Boot services plus a shared library, wired together
with Kafka and a single Postgres database. Nothing calls another service's REST API directly —
the only inter-service communication is Kafka topics. That matters for onboarding: if you're
looking for where `alert-evaluation-service` "calls" `alert-rule-service`, stop — it doesn't.
It reads the `alert_rules` table directly.

```
CoinGecko (external)
     │  polled every 45s
     ▼
price-ingestion-service ──publishes──▶ [ price-ticks ] ──consumed by──▶ alert-evaluation-service
                                          (Kafka topic)                          │
                                                                    reads/writes alert_rules table
                                                                    (shared with alert-rule-service)
                                                                                  │
                                                                     publishes when a rule fires
                                                                                  ▼
                                                                          [ alert-triggers ]
                                                                           (Kafka topic)
                                                                                  │
                                                                                  ▼
                                                                        notification-service
                                                                     (persists + broadcasts)
```

`alert-rule-service` sits off to the side of that pipeline — it owns the `alert_rules` table
(via Flyway) and is the only service that lets a user create/list/delete/toggle rules. It has
no Kafka involvement at all; it's a plain CRUD REST API.

## The five modules

| Module | What it does | Talks to Kafka? | Has a REST API? | Owns a DB table? |
|---|---|---|---|---|
| `price-ingestion-service` | Polls CoinGecko on a schedule, publishes a `PriceTick` per symbol | Produces `price-ticks` | No | No |
| `alert-rule-service` | CRUD for user-defined alert rules | No | **Yes — `/api/alert-rules`** | `alert_rules` |
| `alert-evaluation-service` | Consumes price ticks, checks them against active rules, applies a cooldown, fires events | Consumes `price-ticks`, produces `alert-triggers` | No | No (reads/writes `alert_rules`, but doesn't own its schema) |
| `notification-service` | Consumes trigger events, persists them, pushes them live over WebSocket | Consumes `alert-triggers` | **Yes — `/api/notifications`, plus a WebSocket endpoint** | `notifications` |
| `commons` | Shared DTOs/enums/topic-name constants (`PriceTick`, `AlertTriggeredEvent`, `AlertCondition`, `KafkaTopics`) used by everyone above | — | — | — |

Only two of these — `alert-rule-service` and `notification-service` — are exposed to the
frontend at all. `price-ingestion-service` and `alert-evaluation-service` are pure backend
plumbing; there's no browser-facing surface for either.

## Walking the pipeline

1. **`price-ingestion-service`** is configured (`ingestion.symbols` in its `application.yml`)
   with a fixed list of coins — currently BTC, ETH, SOL — and polls CoinGecko's REST API every
   `ingestion.poll-interval` (default 45s). For each symbol it publishes a `PriceTick` (`symbol`,
   `price`, `currency`, `timestamp`) to the `price-ticks` Kafka topic. It's a one-way producer;
   nothing reads a REST response from it.

2. **`alert-rule-service`** owns the `alert_rules` table and exposes plain CRUD. This is the
   *only* place a rule is created, listed, deleted, or toggled — see API table below.

3. **`alert-evaluation-service`**'s `PriceTickListener` consumes every `PriceTick` off
   `price-ticks`. For each tick it looks up active rules for that symbol (reading the same
   `alert_rules` table `alert-rule-service` writes to — they're two independent JPA mappings
   onto one physical table, deliberately kept separate so the two services can be deployed
   independently), evaluates `PRICE_ABOVE`/`PRICE_BELOW` conditions, and respects a per-rule
   cooldown (`evaluation.cooldown`, default 15 minutes) so one sustained price movement doesn't
   spam repeat alerts. When a rule fires, it publishes an `AlertTriggeredEvent` (`ruleId`,
   `userId`, `symbol`, `condition`, `threshold`, `observedPrice`, `triggeredAt`) to the
   `alert-triggers` topic.

4. **`notification-service`**'s `AlertTriggeredListener` consumes `alert-triggers`, persists
   each one as a row in its own `notifications` table, and separately broadcasts it live over
   STOMP/WebSocket. It also exposes a REST endpoint to fetch notification history. Both the
   REST list and the WebSocket push carry the same shape (`NotificationPayload`).

## What the frontend actually talks to

The Next.js app never calls a backend service directly for REST — it goes through its own
Route Handlers under `frontend/app/api/**`, which act as a thin server-side proxy
(`frontend/lib/backend.ts`'s `proxyFetch`). This exists so the browser only ever talks to the
Next.js origin, avoiding CORS on `alert-rule-service`. The one exception is the WebSocket
connection, which the browser makes directly to `notification-service` (a Next.js Route Handler
can't proxy a WS upgrade), which is why `notification-service` has CORS configured for
`http://localhost:3000` on its STOMP endpoint specifically.

Ports: `alert-rule-service` is `:8081`, `notification-service` is `:8082` (both map container
port 8080 externally via `docker-compose.yml`; `frontend/lib/backend.ts` defaults to these same
values via `ALERT_RULE_SERVICE_URL`/`NOTIFICATION_SERVICE_URL` env vars).

### `alert-rule-service` (`:8081`) — backs the alert rules UI

| Frontend proxy route | Backend endpoint | Purpose |
|---|---|---|
| `GET /api/alert-rules?userId=` | `GET /api/alert-rules?userId=` | List a user's alert rules |
| `POST /api/alert-rules` | `POST /api/alert-rules` | Create a rule (`userId`, `symbol`, `condition`, `threshold`) |
| `DELETE /api/alert-rules/[id]` | `DELETE /api/alert-rules/{id}` | Delete a rule |
| `PATCH /api/alert-rules/[id]/toggle-active` | `PATCH /api/alert-rules/{id}/toggle-active` | Flip a rule's active/inactive state |

`condition` is one of `PRICE_ABOVE` / `PRICE_BELOW` (the `AlertCondition` enum, shared via
`commons` and mirrored in `frontend/lib/types.ts`).

### `notification-service` (`:8082`) — backs the notifications panel

| Frontend access | Backend endpoint | Purpose |
|---|---|---|
| `GET /api/notifications?userId=` (via Next.js proxy) | `GET /api/notifications?userId=` | Notification history for a user |
| Direct browser WebSocket to `ws://localhost:8082/ws`, topic `/topic/notifications` | STOMP endpoint `/ws` | Live push the instant a `notifications` row is created |

There's currently no per-user filtering on the WebSocket broadcast — it's a single shared
`/topic/notifications` topic, everything connected gets everything. This is a known, deliberately
deferred gap tied to auth not existing yet (see 
"Things that'll trip you up" below).

## Things that'll trip you up

- **`userId` is a plain query param, not derived from auth.** There's no auth layer yet — every
  request just passes whatever `userId` the frontend hardcodes/holds in state. Don't go looking
  for a session or JWT; it doesn't exist yet.
- **The two services sharing `alert_rules`/`price_alerts` DB is intentional, not an oversight.**
  `alert-rule-service` and `alert-evaluation-service` each have their own JPA entity for the same
  table so they can be deployed independently. If you change the schema, both entities need
  updating, and only `alert-rule-service` owns the Flyway migration.
- **If notifications aren't showing up in the UI**, the failure could be at any of four hops:
  CoinGecko poll → `price-ticks` → evaluation/cooldown logic → `alert-triggers` →
  `notification-service`. `kafka-ui` (`http://localhost:8090` when the stack is running) is the
  fastest way to check whether a message actually made it onto a given topic before you start
  reading service code.
- **Full backend architecture detail, Flyway/Kafka gotchas, and the Spring Boot 4.1 module-
  splitting traps** live in `agents/ARCHITECTURE.md`, `agents/BUILD.md`, and `agents/GOTCHAS.md`
  at the repo root — worth a skim before you touch any service's Spring config.
