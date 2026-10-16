# syntax=docker/dockerfile:1.6
# multi-stage build для Spring Boot приложения

# ==================== ЭТАП 1: Сборка проекта ====================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Копируем корневой pom.xml (он задает modules)
COPY pom.xml .

# Копируем pom.xml модулей (относительно context: ./backend)
COPY monitoring-api/pom.xml ./monitoring-api/pom.xml
COPY monitoring-auth/pom.xml ./monitoring-auth/pom.xml
COPY monitoring-apps-management/pom.xml ./monitoring-apps-management/pom.xml
COPY monitoring-collector/pom.xml ./monitoring-collector/pom.xml

# Скачиваем зависимости Maven
RUN mvn dependency:go-offline -B

# Копируем весь исходный код
COPY . .

# Собираем проект. Maven автоматически определяет порядок сборки из <modules> в pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# ==================== ЭТАП 2: Финальный образ ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем non-root пользователя для безопасности
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Копируем JAR из модуля monitoring-api (точка входа приложения)
COPY --from=builder --chown=appuser:appgroup /app/monitoring-api/target/monitoring-api-*.jar app.jar

USER appuser

EXPOSE 8080

# Health check через Spring Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Запуск приложения
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
