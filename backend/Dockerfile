# syntax=docker/dockerfile:1.6

# ЭТАП 1: Сборка проекта
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 1. Копируем корневой pom.xml (Maven узнает отсюда список всех модулей)
COPY pom.xml .

# 2. МАГИЯ: Копируем ВСЕ pom.xml из подпапок, сохраняя структуру директорий!
# Docker автоматически положит monitoring-api/pom.xml в ./monitoring-api/pom.xml и так далее.
COPY */pom.xml ./

# 3. Скачиваем зависимости. 
# Этот шаг перезапустится ТОЛЬКО если ты изменишь какой-то pom.xml.
RUN mvn dependency:go-offline -B

# 4. Копируем ВЕСЬ исходный код (то, что не запрещено в .dockerignore)
COPY . .

# 5. Собираем весь проект. Maven сам построит правильный порядок сборки 
# на основе <modules> в корневом pom.xml.
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# ЭТАП 2: Финальный легкий образ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем non-root пользователя
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Копируем JAR из модуля, который является точкой входа (обычно это *-api или *-app)
# Если модуль называется иначе, поменяй monitoring-api на его имя.
COPY --from=builder --chown=appuser:appgroup /app/monitoring-api/target/*.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]