# Frontend kickoff: repo structure, stack, and backend integration

## Context

The backend (Maven multi-module: `commons`, `price-ingestion-service`, `alert-rule-service`, `alert-evaluation-service`, `notification-service`) is at a basic-but-working stage and ready to be wired up to a UI. Before writing any frontend code, we grilled through the open architectural questions the user raised: where the frontend should live, what stack to build it with, how it should talk to the backend (especially given user accounts/auth are coming later and the system is expected to grow), and how to handle the notification-service's WebSocket integration specifically. This plan captures the decisions reached and the concrete first steps to act on them.

Key theme throughout: several decisions (API gateway, per-user WebSocket routing, real auth) are being deliberately deferred rather than guessed at now, because they depend on an auth mechanism that hasn't been chosen yet. Building that infra prematurely would mean designing against unknown requirements. Everything below is scoped to be easy to extend later without rework.

## Decisions reached

1. **Repo structure**: keep the frontend in this same repo (monorepo), not a separate repository. A single dev, no CI/CD yet, and a project of this size get more value from atomic commits across a Kafka DTO change and its matching frontend change than from polyrepo isolation.

2. **Directory placement**: new top-level `frontend/` directory, sitting alongside the Maven modules but **not** listed as a `<module>` in the root `pom.xml` — fully outside the Maven reactor, with its own `package.json`/npm toolchain, own `.gitignore` entries (`node_modules/`, `.next/`), and own README for run instructions. `./mvnw package` at the root remains JVM-only and unaffected.

3. **Stack**: Next.js (App Router, TypeScript). Revised from an earlier Vite + React + TypeScript SPA decision — Next.js's server-side features (Route Handlers) are now being deliberately used for the REST gateway (see decision 4). Deliberately staying within standard Next.js conventions: default `next dev`/`next start`, no custom server. An earlier version of this plan considered a custom server to also proxy the WebSocket connection, but that was dropped — Route Handlers don't support WebSocket upgrades on the standard Node runtime, and Next.js's own guidance when you need WebSockets is to run that connection separately rather than fight the framework to proxy it (see decision 6).

4. **Backend integration — Next.js Route Handlers as a thin REST gateway**: rather than the browser calling `alert-rule-service` and `notification-service`'s REST endpoints directly, Next.js Route Handlers sit in between. REST calls from the browser hit Next.js API routes (e.g. a catch-all proxy route per backend service), which forward server-side to `alert-rule-service` (`http://localhost:8081`) and `notification-service` (`http://localhost:8082`) and return the response. Because the browser only talks to the Next.js origin for REST, **no CORS configuration is needed on `alert-rule-service`**. This is still just a thin, auth-free indirection seam, not real gateway behavior (no auth, no rate limiting) — that stays deferred until an auth mechanism (sessions vs JWT vs OAuth/OIDC) is actually chosen, same reasoning as before. Note this covers REST only — the WebSocket connection is handled differently (see decision 6).

5. **Dev workflow**: run the frontend with `npm run dev` locally, **not** added to `docker-compose.yml` for now. Since the app runs in the browser, it always calls the backend via host-exposed ports regardless of whether Vite itself is containerized — so containerizing buys only "one command starts everything" at the cost of Docker Desktop-on-macOS bind-mount HMR friction (missed file-watch events, needing `usePolling`). Revisit once the UI is stable and one-command startup for demos/onboarding becomes valuable.

6. **Notification-service WebSocket integration**:
   - Drop `.withSockJS()` from `notification-service`'s `WebSocketConfig` (`notification-service/src/main/java/tom/burrows/notificationservice/websocket/WebSocketConfig.java`) — use plain STOMP-over-WebSocket instead of SockJS fallback. SockJS solves a long-polling-fallback problem (old IE, WebSocket-hostile proxies) that doesn't apply here. This decision is unchanged by the Next.js switch — it's orthogonal to where the browser's WebSocket connection terminates.
   - Transport path: the browser's STOMP client connects **directly** to `notification-service`'s `/ws` endpoint (`ws://localhost:8082/ws`) — not through Next.js. This is the one place the frontend talks to a backend service directly rather than via a Next.js Route Handler, because Next.js doesn't support proxying WebSocket upgrades on the standard Node runtime; trying to make it do so would mean a custom server, which we're deliberately avoiding (see decision 3). Consequently, `notification-service` needs CORS/allowed-origins configured for the browser's dev origin (e.g. `http://localhost:3000`) specifically for the WS handshake — the one CORS requirement in this plan, scoped to this service only.
   - Frontend pattern: on load, `GET /api/notifications?userId=1` (proxied through Next.js to `NotificationController`) for history, then open a STOMP client (`@stomp/stompjs`, `brokerURL: 'ws://localhost:8082/ws'`) subscribed to `/topic/notifications` for live updates.
   - The broadcast topic is currently un-scoped (every client gets every notification — `NotificationService.handle()` does a plain `convertAndSend("/topic/notifications", ...)`, no per-user routing). Rather than build real per-user routing now (which needs Spring's `convertAndSendToUser`/`/user/queue/...` bound to an authenticated `Principal` — backend work that belongs alongside auth, not before it), the frontend filters client-side: only render notifications where `userId === 1`, matching the hardcoded `userId=1` convention already used elsewhere. This is unchanged from the original plan — a placeholder that keeps the UI honest about being per-user scoped, ready to be swapped for real server-side routing once auth exists.

## First implementation steps

Steps 1–3 (scaffold `frontend/` via `create-next-app`, save this plan alongside it, confirm `.gitignore` covers `node_modules`/`.next`) are done — see the "Adding frontend files" / "Removing old frontend/..." commits. What follows is the fleshed-out plan for steps 4–7, written against the actual current code (`notification-service`'s `WebSocketConfig`, `NotificationController`, `AlertRuleController`, and their DTOs).

### Step 4: CORS on `notification-service` for the WS handshake origin

File: `notification-service/src/main/java/tom/burrows/notificationservice/websocket/WebSocketConfig.java`.

Add `setAllowedOrigins("http://localhost:3000")` to the `/ws` endpoint registration. Without it, Spring's WebSocket handshake interceptor defaults to same-origin only and rejects the cross-origin handshake from the browser (`localhost:3000` → `localhost:8082`) outright — this is a hard requirement for the browser-to-`notification-service` WS connection to work at all, not a hardening nice-to-have. This is unrelated to Spring MVC's `@CrossOrigin`/`CorsConfigurationSource` (REST CORS) — nothing in this system needs that, since all REST traffic is proxied server-side through Next.js Route Handlers (decision 4) and never crosses origins in the browser.

### Step 5: Drop SockJS in favor of plain STOMP-over-WS

Same file, same endpoint registration, done together with step 4:

```java
// before
registry.addEndpoint("/ws").withSockJS();
// after
registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:3000");
```

This breaks `NotificationIntegrationTest` as currently written: it connects via `new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))))` against `http://localhost:{port}/ws`. `SockJsClient` always issues a SockJS-protocol `GET /ws/info` probe before falling back to its `WebSocketTransport` — with `.withSockJS()` removed server-side that probe 404s and the test fails, even though raw WebSocket was already the only transport registered. I'll update the test in the same change to connect with a plain client instead:

```java
WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
...
stompClient.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
```

(note the `ws://` scheme instead of `http://` — no SockJS layer to negotiate the scheme for you anymore). Then run `./mvnw -pl notification-service test` to confirm `NotificationIntegrationTest`, `NotificationServiceTest`, and `NotificationControllerTest` all still pass.

### Step 6: Next.js Route Handlers as the REST proxy

Config, `frontend/.env.local` (gitignored via the existing `.env*` rule) and a committed `frontend/.env.example` documenting the same two vars:

```
ALERT_RULE_SERVICE_URL=http://localhost:8081
NOTIFICATION_SERVICE_URL=http://localhost:8082
```

(mirrors the root `.env.example`/`.env` split already used for Postgres credentials; these defaults match `docker-compose.yml`'s host port mappings, so `npm run dev` works against `docker compose up` with zero configuration).

A small shared helper, `frontend/lib/backend.ts`, exporting the two base URLs (read from `process.env`, falling back to the defaults above) plus a `proxyFetch(url, init)` wrapper that forwards the request and relays the upstream status code and JSON body — used four times below, so worth not repeating inline.

Route Handlers to add, each a thin pass-through with no auth (per decision 4), preserving upstream status codes (201 from create, 204 from delete, 404 from `AlertRuleController`'s `AlertRuleNotFoundException` via `GlobalExceptionHandler`) so client code can branch on them:

- `frontend/app/api/alert-rules/route.ts` — `GET` reads `userId` off `request.nextUrl.searchParams` and forwards to `${ALERT_RULE_SERVICE_URL}/api/alert-rules?userId=...`; `POST` forwards the JSON body (`{ userId, symbol, condition, threshold }`, matching `CreateAlertRuleRequest`) to the same path.
- `frontend/app/api/alert-rules/[id]/route.ts` — `DELETE` forwards to `${ALERT_RULE_SERVICE_URL}/api/alert-rules/{id}`.
- `frontend/app/api/alert-rules/[id]/toggle-active/route.ts` — `PATCH` forwards to `${ALERT_RULE_SERVICE_URL}/api/alert-rules/{id}/toggle-active`.
- `frontend/app/api/notifications/route.ts` — `GET` reads `userId`, forwards to `${NOTIFICATION_SERVICE_URL}/api/notifications?userId=...`.

### Step 7: Minimal UI

Replace the `create-next-app` boilerplate in `frontend/app/page.tsx`. Split across Server/Client Components the way the App Router expects:

- `frontend/app/page.tsx` (Server Component) — does the *initial* data fetch server-side, straight against the backend services via `lib/backend.ts`'s base URLs (not through the Route Handlers — those exist for the client's later mutations, and a server-to-own-server round trip on first load would be pure waste). Fetches `GET /api/alert-rules?userId=1` and `GET /api/notifications?userId=1` in parallel with `Promise.all`, passes both arrays as props into a client component.
- `frontend/app/dashboard.tsx` (`"use client"`) — receives the initial rules/notifications as props and renders:
  - A create-rule form (symbol text input, condition `<select>` with `PRICE_ABOVE`/`PRICE_BELOW`, threshold number input) that `POST`s to `/api/alert-rules` with `userId: 1` hardcoded (decision 5's placeholder convention — no auth yet) and prepends the response to local state.
  - The rules list, each row with a toggle-active button (`PATCH /api/alert-rules/{id}/toggle-active`) and a delete button (`DELETE /api/alert-rules/{id}`), updating/removing that row in local state on success.
  - A notifications panel seeded from the server-fetched history, opening a STOMP connection on mount (`useEffect`) *directly* to `notification-service` — not via the Next.js proxy, per decision 6 — using `@stomp/stompjs` (`brokerURL: process.env.NEXT_PUBLIC_NOTIFICATION_WS_URL`), subscribing to `/topic/notifications`, prepending incoming payloads to local state filtered to `userId === 1` client-side (decision 5), and disconnecting on unmount.
- Add `@stomp/stompjs` as a dependency: `npm install @stomp/stompjs` in `frontend/`.
- `NEXT_PUBLIC_NOTIFICATION_WS_URL` (must be `NEXT_PUBLIC_`-prefixed since it's read in the browser, unlike the two service URLs above) defaulting to `ws://localhost:8082/ws`, added to both `.env.local` and `.env.example`.

## Verification

- `./mvnw -pl notification-service test` after steps 4–5, to confirm `NotificationIntegrationTest` (updated per above) still passes against the plain-WebSocket endpoint, alongside the existing unit tests.
- `docker compose up` to bring up the backend, then `cd frontend && npm install && npm run dev`: create a rule and confirm it appears via the REST proxy, toggle and delete it, then trigger an alert end-to-end (via `alert-evaluation-service`'s flow) and confirm it appears live in the notifications panel without a page refresh, over a STOMP connection made directly from the browser to `notification-service`.
