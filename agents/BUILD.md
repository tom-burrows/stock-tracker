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

# Start infrastructure (Kafka + Postgres) and all four services
# (price-ingestion-service, alert-rule-service, alert-evaluation-service, notification-service) plus kafka-ui
docker compose up

# Build a Docker image with full output (useful for diagnosing Maven errors inside Docker)
docker compose build --no-cache --progress=plain price-ingestion-service 2>&1 | tee build.log
```

kafka-ui is available at http://localhost:8090 when the stack is running. A Postman collection (`postman/Stock-Prices.postman_collection.json`) covers `alert-rule-service` CRUD and `notification-service` (list + STOMP broadcast) for manual testing.