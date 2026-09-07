# syntax=docker/dockerfile:1

# ---- Build stage --------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so they are cached across source-only changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S bizpilot && adduser -S bizpilot -G bizpilot
COPY --from=build /app/target/bizpilot-backend.jar app.jar

# Phase 13: local document storage root, pre-created and owned by the
# runtime user before the named volume is first attached (docker-compose.yml
# mounts document_storage here) so writes never fail on permissions.
RUN mkdir -p /app/uploads && chown bizpilot:bizpilot /app/uploads

USER bizpilot

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
