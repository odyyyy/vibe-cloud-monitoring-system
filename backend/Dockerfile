# syntax=docker/dockerfile:1.6
# multi-stage build for the Spring Boot application

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

# Pre-fetch Maven dependencies
RUN mvn dependency:go-offline -B

# Copy all sources
COPY . .

# Build the project. Maven resolves the build order from <modules> in pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# ==================== STAGE 2: runtime image ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy the JAR from the monitoring-api module (application entry point)
COPY --from=builder --chown=appuser:appgroup /app/monitoring-api/target/monitoring-api-*.jar app.jar

USER appuser

EXPOSE 8080

# Health check via Spring Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Start the application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
