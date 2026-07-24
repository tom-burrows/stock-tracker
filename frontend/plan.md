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

1. Create `frontend/` at the repo root (scaffold via `npx create-next-app` with TypeScript and the App Router).
2. Save this plan as `frontend/plan.md` in the new directory, as requested, so it travels with the frontend code as a record of the architectural decisions behind it.
3. Add `frontend/node_modules/` and `frontend/.next/` to `.gitignore`.
4. Configure CORS on `notification-service` to allow the Next.js dev server's origin (default `http://localhost:3000`), for the WS handshake only.
5. Remove `.withSockJS()` from `notification-service`'s `WebSocketConfig`, confirm existing tests still pass.
6. Add Next.js API routes that proxy REST calls server-side to `alert-rule-service` and `notification-service`.
7. Build the minimal UI: fetch/display alert rules via the Next.js `/api/...` proxy routes, and a notifications panel that fetches history via the proxied `GET /api/notifications?userId=1` and subscribes to `/topic/notifications` via `@stomp/stompjs` connected directly to `notification-service`, filtering to `userId === 1`.

## Verification

- `./mvnw -pl notification-service test` after removing SockJS, to confirm the existing STOMP/WebSocket integration test (`NotificationIntegrationTest`) still passes against the plain-WebSocket endpoint.
- `docker compose up` to bring up the backend, then `cd frontend && npm run dev`, and manually verify in-browser: alert rules list loads via the Next.js REST proxy, and triggering an alert (e.g. via `alert-evaluation-service`'s flow) shows up live in the notifications panel without a page refresh, via a STOMP connection made directly from the browser to `notification-service`.
