# syntax=docker/dockerfile:1.6
# Multi-stage build for the Spring Boot application (Maven -> JRE 21).

# ==================== STAGE 1: build ====================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy the root pom.xml (it declares the modules)
COPY pom.xml .

# Copy the module pom.xml files (paths are relative to the context: ./backend)
COPY monitoring-common/pom.xml ./monitoring-common/pom.xml
COPY monitoring-api/pom.xml ./monitoring-api/pom.xml
COPY monitoring-auth/pom.xml ./monitoring-auth/pom.xml
COPY monitoring-apps-management/pom.xml ./monitoring-apps-management/pom.xml
COPY monitoring-collector/pom.xml ./monitoring-collector/pom.xml
COPY monitoring-dashboards/pom.xml ./monitoring-dashboards/pom.xml
COPY monitoring-healthcheck/pom.xml ./monitoring-healthcheck/pom.xml
COPY monitoring-alerts/pom.xml ./monitoring-alerts/pom.xml
COPY monitoring-audit/pom.xml ./monitoring-audit/pom.xml
COPY monitoring-retention/pom.xml ./monitoring-retention/pom.xml
COPY monitoring-revisions/pom.xml ./monitoring-revisions/pom.xml
COPY monitoring-statuspages/pom.xml ./monitoring-statuspages/pom.xml

# Pre-fetch Maven dependencies
RUN mvn dependency:go-offline -B

# Copy all sources
COPY . .

# Build the project. Maven resolves the build order from <modules> in pom.xml.
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# ==================== STAGE 2: runtime image ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy the JAR from the monitoring-api module (application entry point)
COPY --from=builder --chown=appuser:appgroup /app/monitoring-api/target/monitoring-api-*.jar /app/app.jar

# Extra JVM flags can be supplied at deploy time, for example:
#   JAVA_OPTS="-XX:MaxRAMPercentage=60 -Xms256m"
ENV JAVA_OPTS=""

USER appuser

EXPOSE 8080

# Readiness includes database connectivity, so the container is unhealthy while MySQL is
# unreachable and recovers automatically when the database comes back.
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --output-document=- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

# Use the container-aware defaults, but keep them overridable through JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -jar /app/app.jar"]
