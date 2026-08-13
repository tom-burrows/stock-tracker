## Architecture

Next.js (App Router, TypeScript, React 19, Tailwind v4), scaffolded via `create-next-app`.

**Current state**: still the unmodified scaffold — `app/page.tsx` and `app/layout.tsx` are the default `create-next-app` files. None of the following, planned in `../plan.md`, are built yet:

- Route Handlers proxying REST calls to `alert-rule-service` (`:8081`) and `notification-service` (`:8082`), so the browser only ever talks to the Next.js origin for REST.
- A `lib/backend.ts` helper exporting the two backend base URLs.
- The dashboard UI (alert-rule create/list/toggle/delete, notifications panel).
- The one exception to the proxy pattern: a STOMP/WebSocket client connecting from the browser *directly* to `notification-service`'s `/ws` (Route Handlers can't proxy WS upgrades on the standard Node runtime).

Check `../plan.md`'s "First implementation steps" for what's actually been built vs. still planned before assuming a given piece exists.

**Tailwind v4**: config is CSS-first — there is no `tailwind.config.js`. Theme tokens (`--color-background`, fonts, etc.) are declared with `@theme inline` directly in `app/globals.css`.

For backend service/topic/schema architecture (what the frontend will eventually talk to), see `../../agents/ARCHITECTURE.md`.
