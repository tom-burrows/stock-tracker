# User account system — new `user-service` module + per-user notification routing

## Context

There is currently no concept of a "user" anywhere in the backend — `AlertRule.userId` and
`AlertTriggeredEvent.userId` are bare `Long`s the frontend hardcodes to `1`
(`notes/summaries/backend-overview.md`, memory `frontend-auth-deferred-work`). This work
introduces the first real user account: registration, login, profile, and per-user symbol
preferences (which symbols a user tracks vs. displays), plus wires notification delivery to
actually scope by the authenticated user instead of broadcasting to everyone.

Confirmed decisions:
- **New standalone module** `user-service`, mirroring `alert-rule-service`'s structure — own
  Postgres tables, own Flyway history, own REST API. Nothing here is folded into an existing
  service.
- **Stateless JWT**, not server-side sessions — no shared session store needed across services,
  and it's the right shape for authenticating the WebSocket handshake below. JWT itself isn't
  third-party SSO, but token *issuance* is kept as its own step in `AuthService` (separate from
  password verification) so a future OIDC/OAuth login can mint the same token without rework.
- **Two separate symbol lists** — `tracked_symbols` (drives alerting/backend interest) and
  `displayed_symbols` (drives dashboard UI) — independent, not derived from each other.
- **Notification WebSocket routing is fully wired now**: `notification-service` gets a STOMP
  CONNECT auth interceptor and switches from a shared `/topic/notifications` broadcast to
  `convertAndSendToUser`. This is 100% backend Spring config — no Next.js files touched — but it
  is a **breaking change to the currently-working live-notification feature**: the frontend
  doesn't send a token today, so after this ships the STOMP client in
  `frontend/app/dashboard.tsx` will fail to connect (CONNECT frames without a valid
  `Authorization` header are now rejected) until a frontend follow-up sends one. This is expected
  fallout of "linking notifications to specific user IDs," not an oversight.
- Explicitly **out of scope**: protecting `alert-rule-service`/`notification-service`'s REST
  endpoints with JWT, and any Next.js/frontend changes (Route Handlers, dashboard, STOMP client).
  Also out of scope: cross-service `userId` referential-integrity checks (e.g. `alert-rule-service`
  calling `user-service` to validate a `userId` exists) — that would violate the existing
  Kafka-only inter-service communication rule in `agents/ARCHITECTURE.md`.

## Part 1 — `user-service` module

Package root: `tom.burrows.userservice`. Structure mirrors `alert-rule-service` exactly
(`domain/`, `dto/`, `repository/`, `service/`, `web/`), plus a new `security/` package.

### Data model — `src/main/resources/db/migration/V1__create_users_and_symbol_tables.sql`

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tracked_symbols (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, symbol)
);

CREATE TABLE displayed_symbols (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, symbol)
);
```

Unlike `alert_rules.user_id` (a bare `Long` because it's referenced *cross-service*), these FKs
are real Postgres foreign keys — `user-service` is the sole owner and sole writer of all three
tables, so referential integrity is safe here in a way it deliberately isn't across service
boundaries.

### Entities (`domain/`)

`User`, `TrackedSymbol`, `DisplayedSymbol` — same Lombok `@Getter @Setter` + `@PrePersist`
`createdAt` stamping style as `alert-rule-service/src/main/java/.../domain/AlertRule.java`.
`User` has no `@Enumerated` field, just plain columns matching the migration above.

### DTOs (`dto/`, all records with `jakarta.validation.constraints`)

- `RegisterRequest(String username, String email, String password)` — `@NotBlank`, `@Email`,
  `@Size(min = 8)` on password.
- `LoginRequest(String username, String password)`
- `LoginResponse(String token)`
- `UserProfileResponse(Long id, String username, String email, Instant createdAt)` with a static
  `from(User)` factory, same convention as `AlertRuleResponse.from(...)`.
- `UpdateProfileRequest(String email)` — username is immutable after registration, keep it simple.
- `ChangePasswordRequest(String currentPassword, String newPassword)`
- `SymbolRequest(String symbol)` and `SymbolResponse(String symbol, Instant createdAt)` — shared
  shape reused by both the tracked- and displayed-symbol endpoints.

### Repositories (`repository/`)

`UserRepository extends JpaRepository<User, Long>` with `findByUsername`, `existsByUsername`,
`existsByEmail`. `TrackedSymbolRepository` / `DisplayedSymbolRepository` each with
`findByUserId(Long)` and `deleteByUserIdAndSymbol(Long, String)` — same derived-query style as
`AlertRuleRepository.findByUserId`.

### Security (`security/`) — new package, no existing precedent in this repo

- `JwtService` — wraps `io.jsonwebtoken` (jjwt): `generateToken(Long userId)` and
  `extractUserId(String token)` (throws on invalid/expired). Signing key and expiry come from
  `app.jwt.secret` / `app.jwt.expiration-ms` (see application.yml below).
- `JwtAuthenticationFilter extends OncePerRequestFilter` — reads `Authorization: Bearer <token>`,
  on success sets `SecurityContextHolder` with a principal of `Long userId` and no meaningful
  authorities; on missing/invalid token it just continues the chain unauthenticated (Spring
  Security's `authorizeHttpRequests` rules handle the resulting 401).
- `SecurityConfig` — one `SecurityFilterChain` bean: CSRF disabled, `STATELESS` session policy,
  `permitAll` on `/api/auth/**` and `/actuator/health`, `authenticated()` on everything else,
  `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`. A
  `PasswordEncoder` bean (`BCryptPasswordEncoder`).
- Login itself does **not** go through Spring Security's `AuthenticationManager`/
  `UserDetailsService` machinery — `AuthService.login()` just looks up the user by username and
  calls `passwordEncoder.matches(raw, user.getPasswordHash())` directly. Full `AuthenticationManager`
  wiring would be unused ceremony for a single credential check.
- Controllers read the authenticated user via `Authentication authentication` injected by Spring
  MVC and cast `(Long) authentication.getPrincipal()` — no custom `@AuthenticationPrincipal`
  resolver needed for a plain `Long`.

Note on GOTCHAS.md's warning against `spring-boot-starter-test-classic` (it pulls
`spring-boot-security-test` without a production Security module, breaking context loading):
that warning does **not** apply to `user-service`, since this module *does* have a real
`spring-boot-starter-security` production dependency. Depend on `spring-boot-security-test`
directly (not the `-test-classic` umbrella) for test support.

### Services (`service/`)

- `AuthService` — `register(RegisterRequest)` (checks `existsByUsername`/`existsByEmail`, throws
  `UsernameAlreadyExistsException`/`EmailAlreadyExistsException`, hashes password, saves) and
  `login(LoginRequest)` (throws `InvalidCredentialsException` on mismatch, else
  `jwtService.generateToken(user.getId())`).
- `UserProfileService` — `getProfile(userId)`, `updateProfile(userId, UpdateProfileRequest)`,
  `changePassword(userId, ChangePasswordRequest)` (verifies current password first).
- `TrackedSymbolService`, `DisplayedSymbolService` — each a thin `list(userId)` /
  `add(userId, symbol)` / `remove(userId, symbol)`, same shape as `AlertRuleService`. Two small
  services rather than one generic abstraction, matching this codebase's preference for
  straightforward duplication over premature generalization.
- Domain exceptions in `service/`: `UsernameAlreadyExistsException`, `EmailAlreadyExistsException`,
  `InvalidCredentialsException`, `UserNotFoundException` — plain `RuntimeException` subclasses,
  same style as `AlertRuleNotFoundException`.

### Controllers (`web/`)

- `AuthController` — `POST /api/auth/register` (201), `POST /api/auth/login` (200,
  `LoginResponse`).
- `UserProfileController` — `GET /api/users/me`, `PATCH /api/users/me`,
  `PATCH /api/users/me/password`. `userId` always comes from the JWT principal, never a path/query
  param — this is the actual point of this work, replacing the bare-`userId`-query-param pattern
  every other service still uses.
- `SymbolPreferenceController` — `GET/POST /api/users/me/tracked-symbols`,
  `DELETE /api/users/me/tracked-symbols/{symbol}`, and the same three for `/displayed-symbols`.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) — maps the four exceptions above to 409/409/
  401/404, same pattern as `alert-rule-service`'s handler (plain string body, not `ProblemDetail`).
  `@Valid` failures fall through to Spring's default 400, unhandled, matching existing convention.

### Module wiring

- `pom.xml`: parent `tom.burrows:stock-prices`, dependencies `spring-boot-starter-web`,
  `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`,
  `spring-boot-starter-actuator`, `postgresql` (runtime), `spring-boot-flyway` + `flyway-core` +
  `flyway-database-postgresql` (per GOTCHAS.md — `flyway-core` alone won't autoconfigure), `lombok`,
  and `jjwt-api`/`jjwt-impl`/`jjwt-jackson`. No `commons` dependency and no `spring-kafka` — this
  module has no Kafka involvement, keep it self-contained like `alert-rule-service` was kept lean
  before Kafka was ever added to it. Test deps mirror `alert-rule-service/pom.xml` plus
  `spring-boot-security-test`.
- Root `pom.xml`: add `<module>user-service</module>`; add `jjwt-api`/`jjwt-impl`/`jjwt-jackson`
  to `<dependencyManagement>` with a pinned version (e.g. `0.12.6`), same pattern as the existing
  `testcontainers-bom` pin.
- `application.yml` — same `spring.datasource`/`spring.jpa`/`spring.flyway` block as
  `alert-rule-service/src/main/resources/application.yml` (own `flyway.table:
  flyway_schema_history_user_service`), plus:
  ```yaml
  app:
    jwt:
      secret: ${JWT_SECRET:local-dev-only-secret-override-me-in-real-envs-must-be-32-bytes-plus}
      expiration-ms: ${JWT_EXPIRATION_MS:86400000}
  ```
- `Dockerfile` — copy of `alert-rule-service/Dockerfile`'s two-stage pattern (root-context POM
  pre-fetch, then `commons/src` + `user-service/src` copy, then package).
- `docker-compose.yml` — new `user-service` block, port `8083:8080` (next free port after
  `alert-rule-service`'s 8081 / `notification-service`'s 8082), env vars
  `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (same pattern as `alert-rule-service`'s block) plus
  `APP_JWT_SECRET: ${JWT_SECRET}`, `depends_on: postgres: condition: service_healthy`, same
  `curl .../actuator/health` healthcheck.
- `.env.example` — add `JWT_SECRET=change-me-to-a-long-random-string-at-least-32-bytes`.

### Tests

Same four-layer pattern as `alert-rule-service`, all named `*Test`/`*IntegrationTest` (never
`*IT`, per the Surefire gotcha):
- Unit: `service/AuthServiceTest`, `service/UserProfileServiceTest`,
  `service/TrackedSymbolServiceTest`, `service/DisplayedSymbolServiceTest`,
  `security/JwtServiceTest` (token generation/parsing/expiry) — pure Mockito, no Spring context.
- Web slice: `web/AuthControllerTest`, `web/UserProfileControllerTest`,
  `web/SymbolPreferenceControllerTest` — `@WebMvcTest` with `@AutoConfigureMockMvc(addFilters =
  false)` to test controller logic without the real JWT filter chain in the way (mirrors why
  `AlertRuleControllerTest` builds its own `new ObjectMapper()` rather than relying on Spring's
  Jackson 3 bean — same "isolate the slice from cross-cutting concerns" instinct).
- Repository: `repository/UserRepositoryIntegrationTest`,
  `repository/TrackedSymbolRepositoryIntegrationTest`,
  `repository/DisplayedSymbolRepositoryIntegrationTest` — `@DataJpaTest` +
  `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers Postgres, identical to
  `AlertRuleRepositoryIntegrationTest`.
- Full stack: `UserServiceIntegrationTest` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `@AutoConfigureTestRestTemplate` + Testcontainers, exercising the real filter chain: register →
  login → use token on `/api/users/me` (200) → same call with no/garbage token (401) → duplicate
  username/email on register (409) → wrong password on login (401) → tracked/displayed symbol
  add/list/remove round trip.

## Part 2 — Per-user WebSocket routing in `notification-service`

Current state (`notification-service/src/main/java/tom/burrows/notificationservice/`):
`WebSocketConfig` only enables `/topic`, `NotificationService.handle()` does a plain
`convertAndSend("/topic/notifications", ...)` to everyone, and `NotificationController` already
scopes its REST list correctly via `findByUserIdOrderByCreatedAtDesc` — only the WS push is
unscoped.

Changes:
- `pom.xml`: add `jjwt-api`/`jjwt-impl`/`jjwt-jackson` (same pinned version as `user-service`).
  No `spring-boot-starter-security` needed here — only the STOMP handshake is being gated, not
  the REST endpoints, so a hand-rolled interceptor is simpler than pulling in a full filter chain.
- New `security/JwtValidator.java` — same shape as `user-service`'s `JwtService.extractUserId`,
  reading the same `app.jwt.secret` (must match `user-service`'s value — set via the same
  `JWT_SECRET` env var in `docker-compose.yml`, no other coupling between the two services). This
  is a deliberate small duplication, consistent with how `AlertRule`'s JPA mapping is already
  duplicated between `alert-rule-service` and `alert-evaluation-service` for independent
  deployability — no shared "auth" module is introduced.
- New `security/StompPrincipal.java` — trivial `Principal` wrapping the userId string.
- New `security/StompAuthChannelInterceptor.java` implementing `ChannelInterceptor`: on
  `preSend`, if the STOMP command is `CONNECT`, reads the `Authorization` native header, validates
  it via `JwtValidator`, and calls `accessor.setUser(new StompPrincipal(userId))`. An invalid or
  missing token throws, which Spring's STOMP support turns into a rejected CONNECT (client gets
  an ERROR frame / failed connection) — unauthenticated clients can no longer connect at all.
- `WebSocketConfig`:
  - `configureMessageBroker`: change `registry.enableSimpleBroker("/topic")` to
    `registry.enableSimpleBroker("/topic", "/queue")` — required for `convertAndSendToUser`'s
    per-session queue routing to work with the simple broker.
  - Override `configureClientInboundChannel(ChannelRegistration registration)` to register the
    new `StompAuthChannelInterceptor`.
- `NotificationService.handle()`: replace
  `messagingTemplate.convertAndSend("/topic/notifications", payload)` with
  `messagingTemplate.convertAndSendToUser(event.userId().toString(), "/queue/notifications",
  payload)`.
- `application.yml`: add the same `app.jwt.secret: ${JWT_SECRET:...}` block as `user-service`
  (expiration isn't needed here, only validation).
- `docker-compose.yml`: add `APP_JWT_SECRET: ${JWT_SECRET}` to the existing `notification-service`
  block.

### Test updates

`notification-service/src/test/java/tom/burrows/notificationservice/NotificationIntegrationTest.java`
currently connects with no auth and subscribes to `/topic/notifications`
(`stompClient.connectAsync("ws://localhost:" + port + "/ws", ...)` at line 106-109, subscribe at
line 110). Update it to:
- Mint a valid test JWT (same `app.jwt.secret` the test's Spring context uses — set explicitly via
  `@DynamicPropertySource` or a fixed test value) and pass it via STOMP CONNECT headers (the
  `WebSocketStompClient.connectAsync(url, WebSocketHttpHeaders, StompHeaders, StompSessionHandler)`
  overload, with `StompHeaders` containing `Authorization: Bearer <token>`).
- Subscribe to `/user/queue/notifications` instead of `/topic/notifications`.
- Add a second test asserting a CONNECT with no/invalid Authorization header fails to establish a
  session (the `connectAsync(...).get(...)` future completes exceptionally or times out).

## Part 3 — Where this fits with the frontend (not implemented now)

Kept as design notes only, per your instruction not to touch frontend code yet:

- New Next.js Route Handlers under `frontend/app/api/**` would proxy to `user-service` (`:8083`)
  the same way `frontend/lib/backend.ts`'s `proxyFetch` already proxies to `alert-rule-service`/
  `notification-service` — `/api/auth/register`, `/api/auth/login`, `/api/users/me`, etc.
- Once a login flow exists, the frontend stops hardcoding `userId=1` and instead holds the JWT
  (e.g. an httpOnly cookie set by the `/api/auth/login` Route Handler) and threads the real user
  id through.
- **Immediate consequence of Part 2**: the STOMP client in `frontend/app/dashboard.tsx` needs to
  start sending `Authorization: Bearer <token>` in its connect headers, and subscribe to
  `/user/queue/notifications` instead of `/topic/notifications` — until that frontend change
  ships, live notifications in the current UI will stop arriving (the REST notification history
  endpoint is unaffected, since it was already scoped by `userId`).
- Protecting `alert-rule-service`'s and `notification-service`'s REST endpoints with the same JWT
  filter, and updating `proxyFetch` to attach the token, is the natural next increment but is
  explicitly not part of this plan.

This maps directly onto memory `frontend-auth-deferred-work`'s deferred trio: item 2 (per-user WS
routing) is fully addressed by Part 2; items 1 (API gateway auth) and 3 (removing hardcoded
`userId=1`) remain future frontend work.

## Verification

1. `./mvnw -pl user-service -am package` — module builds and its own tests pass.
2. `./mvnw -pl notification-service -am test` — updated `NotificationIntegrationTest` passes
   (valid-token subscribe receives the event on `/user/queue/notifications`; no-token connect is
   rejected).
3. `./mvnw test` from repo root — full reactor build, nothing else regresses.
4. `docker compose up` — `user-service` starts healthy on `:8083` alongside the existing four
   services; `kafka-ui` unaffected (this module doesn't touch Kafka).
5. Manual smoke test via the existing Postman collection pattern (or `curl`):
   `POST /api/auth/register` → `POST /api/auth/login` → `GET /api/users/me` with the returned
   token → `POST /api/users/me/tracked-symbols` → `GET /api/users/me/tracked-symbols` confirms it.
6. Manual WS smoke test: connect a STOMP client with a valid token for user X, trigger an alert
   for user X via the existing pipeline (publish to `alert-triggers` or run the real
   ingestion→evaluation flow), confirm the payload arrives; confirm a client connecting for user Y
   does *not* receive it.
