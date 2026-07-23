# notification-service/Dockerfile

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build

## Copy aggregator pom (do this first, better layer caching)

COPY pom.xml .
COPY commons/pom.xml commons/
COPY price-ingestion-service/pom.xml price-ingestion-service/
COPY alert-rule-service/pom.xml alert-rule-service/
COPY notification-service/pom.xml notification-service/
COPY alert-evaluation-service/pom.xml alert-evaluation-service/

# Pre-fetch dependencies
RUN mvn -B -pl notification-service -am dependency:go-offline

# Copying full source
COPY commons/src commons/src
COPY notification-service/src notification-service/src

# Build this module
RUN mvn -B -pl notification-service -am package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:26-jre-noble
WORKDIR /app
# curl backs docker-compose's healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/notification-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
