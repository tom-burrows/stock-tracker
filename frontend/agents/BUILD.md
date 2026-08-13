## Build & Run

Run from `frontend/` — this directory has its own `package.json`/npm toolchain and is deliberately outside the Maven reactor (not listed in the root `pom.xml`, not containerized; see `../plan.md` decisions 2 and 5).

```bash
npm install    # first time, and after dependency changes
npm run dev      # dev server, http://localhost:3000
npm run build     # production build
npm run start      # run a production build
npm run lint        # eslint (flat config — eslint.config.mjs)
```

No test runner is configured yet (no test script in `package.json`).

For the app to do anything beyond the static scaffold, the backend must also be running — see `../../agents/BUILD.md` (`docker compose up` at the repo root).
