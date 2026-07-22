# Docker Build Debugging Notes — Stock Prices Project

## Get full, untruncated build output from docker-compose

Docker's default build output truncates error messages, which makes it hard to
diagnose failures beyond a generic "exit code: 1". Use this to see the real
`[ERROR]` block (e.g. from Maven) instead of guessing:

```bash
docker compose build --no-cache --progress=plain price-ingestion-service 2>&1 | tee build.log
```

- `--no-cache` — forces a full rebuild so you're not looking at stale cached layers
- `--progress=plain` — disables the collapsed/fancy TTY output and prints full logs
- `2>&1 | tee build.log` — captures both stdout and stderr to a file you can scroll
  back through or grep, while still printing to the terminal

Swap `price-ingestion-service` for any other service name to target it specifically.

## Multi-module Maven + Docker context reminders

- `docker-compose.yml` should set `context: .` (repo root), with
  `dockerfile: <module-name>/Dockerfile` — this keeps all `COPY` paths in the
  Dockerfile relative to the repo root, not the module folder.
- Child module POMs must declare the aggregator POM (`tom.burrows:stock-prices`)
  as `<parent>`, not `spring-boot-starter-parent` directly, or
  `pluginManagement`/`dependencyManagement` won't propagate.
- `dependency:go-offline` can fail on reactor-internal dependencies (like
  `commons`) if only POMs have been copied and no source exists yet to satisfy
  the reactor. If you hit this, copy all module source before running
  `go-offline`/`package`, accepting a less optimal cache layer until the base
  build is stable.
