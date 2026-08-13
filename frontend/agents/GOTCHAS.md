## Gotchas

- **This Next.js version postdates your training data.** `package.json` pins `next@16.2.11` / `react@19.2.4` — newer than what most training data covers, with breaking changes in APIs and conventions vs. what you may expect. Check `node_modules/next/dist/docs/` before writing App Router code that relies on specific API shapes, and heed any deprecation notices you encounter.
- **No `tailwind.config.js`.** Tailwind v4's config is CSS-first — theme tokens live in `@theme inline` inside `app/globals.css`, not a JS/TS config file. Don't go looking for one.
- **The REST proxy pattern doesn't exist yet.** `plan.md` describes Route Handlers proxying to `alert-rule-service`/`notification-service`, but as of writing, no `app/api/**` routes have been added — don't assume a given proxy route is already there without checking.
