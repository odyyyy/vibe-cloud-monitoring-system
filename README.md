# VibeMonitoring — Cloud Observability Platform

Платформа облачного мониторинга в духе Datadog/Grafana: пользователи регистрируются в
SaaS-интерфейсе, подключают свои приложения и инфраструктуру, а затем наблюдают метрики,
логи, состояние сервисов и дашборды. Телеметрию в облако отправляет устанавливаемый в
инфраструктуре клиента **Go-агент** (изначально распространяется как Docker-образ).

> Статус: ранняя стадия. Реализованы аутентификация, управление приложениями (Applications),
> конвейеры метрик и логов (ingest + query), а также дашборды с виджетами
> (Metric chart / Log table / Status). Health-checks пока не реализованы — см. раздел
> [Текущие ограничения](#текущие-ограничения).

---

## Содержание

- [Архитектура](#архитектура)
- [Технологический стек](#технологический-стек)
- [Требования](#требования)
- [Структура репозитория](#структура-репозитория)
- [Быстрый старт через Docker Compose](#быстрый-старт-через-docker-compose)
- [Локальная разработка](#локальная-разработка)
  - [Backend](#backend)
  - [Frontend](#frontend)
  - [Go-агент](#go-агент)
- [Конфигурация окружения](#конфигурация-окружения)
- [Установка агента](#установка-агента)
- [Обзор API](#обзор-api)
- [Тесты](#тесты)
- [Текущие ограничения](#текущие-ограничения)
- [Безопасность](#безопасность)

---

## Архитектура

Проект состоит из трёх частей:

1. **Cloud Backend** — REST API (модульный монолит на Spring Boot): аутентификация,
   организации/проекты/приложения, приём телеметрии, запросы и конфигурация.
2. **Cloud Frontend** — клиентское веб-приложение (React + TypeScript): онбординг,
   мониторинг, просмотр метрик, логов и дашбордов.
3. **Go Agent / Collector** — процесс в инфраструктуре клиента, который собирает
   настроенную телеметрию и отправляет её в облако по аутентифицированному HTTPS.

Логически разделены **control plane** (пользователи, приложения, конфигурация) и
**telemetry data plane** (приём и запросы метрик/логов). В MVP это один деплой.

Backend собран как Maven multi-module проект:

| Модуль | Ответственность |
| --- | --- |
| `monitoring-auth` | Пользователи, регистрация/логин, JWT, Spring Security |
| `monitoring-apps-management` | CRUD приложений и управление API-ключами |
| `monitoring-collector` | Приём метрик/логов (`/api/v1/ingest`) и запросы метрик/логов (`/api/v1/metrics/query`, `/api/v1/logs`) |
| `monitoring-dashboards` | CRUD дашбордов и виджетов (`/api/v1/dashboards`) |
| `monitoring-api` | Точка входа, конфигурация, композиция модулей, Actuator, OpenAPI |

Поток телеметрии:

```
Пользователь создаёт Application в UI
        │
        ▼
Платформа выдаёт scoped API-ключ (X-API-Key)
        │
        ▼
Go-агент шлёт батч метрик POST /api/v1/ingest/metrics
        │
        ▼
Backend определяет tenant по ключу, валидирует и сохраняет
        │
        ▼
Frontend запрашивает GET /api/v1/metrics/query и рисует график
```

---

## Технологический стек

**Backend**
- Java 21, Spring Boot 3.2.5 (Spring Web, Spring Security, Spring Data JPA)
- JWT (jjwt 0.12.5), Jakarta Bean Validation, Lombok
- MySQL 8 (прод/Compose) или H2 in-memory (локальная разработка)
- Flyway-миграции, Spring Boot Actuator, springdoc OpenAPI (Swagger UI)
- JUnit 5 / Spring Boot Test

**Frontend**
- React 18, TypeScript (strict), Vite
- React Router 6, TanStack Query v4, Zustand
- React Hook Form + Zod
- Tailwind CSS + компоненты в стиле shadcn/ui (Radix UI), Sonner (тосты), Recharts (графики),
  React Grid Layout (сетка дашбордов)

**Agent**
- Go 1.26, `gopsutil/v4` (CPU/memory/disk), `yaml.v3`
- Собственный HTTP-клиент с bounded retries и exponential backoff

---

## Требования

Для запуска «всё в Docker»:
- Docker Engine 24+ и Docker Compose v2

Для локальной разработки без Docker:
- JDK 21
- Maven 3.9+ (или входящий `mvnw`/`mvnw.cmd`)
- Node.js 18+ и npm
- Go 1.26+ (только для агента)

---

## Структура репозитория

```
cloud-monitoring/
├── backend/                     # Maven multi-module Spring Boot
│   ├── monitoring-auth/
│   ├── monitoring-apps-management/
│   ├── monitoring-collector/
│   ├── monitoring-dashboards/   # дашборды и виджеты
│   ├── monitoring-api/          # entry point (порт 8080)
│   ├── docker/mysql/initdb/     # init-скрипты MySQL
│   ├── Dockerfile
│   ├── pom.xml
│   └── .env                     # пример конфигурации (плейсхолдеры)
├── frontend/                    # React + Vite (порт 3000)
│   ├── src/
│   ├── Dockerfile.dev
│   └── package.json
├── agent/                       # Go-агент
│   ├── cmd/agent/
│   ├── config.yaml              # пример конфигурации (плейсхолдеры)
│   └── Dockerfile
├── compose.yaml                 # mysql + app + frontend
├── AGENTS.md                    # правила и гайдлайны разработки
└── TASKS.md                     # план задач
```

---

## Быстрый старт через Docker Compose

`compose.yaml` поднимает MySQL, backend и frontend для разработки.

```bash
# 1. Отредактируйте backend/.env — Compose читает этот файл (env_file) для mysql и app.
#    Значения по умолчанию подходят для локального запуска, но JWT_SECRET
#    обязательно смените перед публичным запуском.

# 2. Поднимите сервисы
docker compose up -d mysql app frontend

# либо с авто-перезагрузкой при изменениях (watch mode, Compose v2.22+)
docker compose watch
```

После старта:

| Сервис | URL |
| --- | --- |
| Frontend (Vite dev) | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator health | http://localhost:8080/actuator/health |
| MySQL | localhost:3306 (база `monitoring_db`) |

Остановить и удалить данные:

```bash
docker compose down          # остановить
docker compose down -v       # остановить и удалить volume с данными MySQL
```

> Агент в compose отключён и вынесен за профиль `agent` (см. закомментированный блок
> в `compose.yaml`). Смотрите [Установка агента](#установка-агента).

---

## Локальная разработка

### Backend

По умолчанию (без `SPRING_DATASOURCE_URL`) backend использует **H2 in-memory**, поэтому
запускается без внешней БД.

```bash
cd backend

# сборка всех модулей и тесты
./mvnw clean test            # Windows: mvnw.cmd clean test

# сборка
./mvnw clean package -DskipTests

# запуск (порт 8080)
java -jar monitoring-api/target/monitoring-api-*.jar
```

С профилем `dev` включается конфигурация Spring Security для разработки:

```bash
./mvnw -pl monitoring-api -am spring-boot:run -Dspring-boot.run.profiles=dev
```

Полезные точки:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- H2 console (при включённом `SPRING_H2_CONSOLE_ENABLED=true`): `http://localhost:8080/h2-console`
- Actuator health: `http://localhost:8080/actuator/health`

Для работы с MySQL вместо H2 задайте переменные из `backend/.env`
(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
`SPRING_DATASOURCE_DRIVER_CLASS_NAME`). Миграции применяются Flyway-скриптами из
`src/main/resources/db/migration`.

### Frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:3000
```

Vite проксирует путь `/api` на backend (`VITE_BACKEND_PROXY_TARGET` или `VITE_BACKEND_URL`,
по умолчанию `http://localhost:8080`), поэтому CORS не требуется в разработке.

Доступные скрипты:

| Команда | Назначение |
| --- | --- |
| `npm run dev` | dev-сервер Vite |
| `npm run build` | production-сборка в `dist/` |
| `npm run preview` | предпросмотр собранного бандла |
| `npm run typecheck` | `tsc --noEmit` (strict) |
| `npm test` | unit-тесты (Vitest) |

### Go-агент

```bash
cd agent
go build -o agent ./cmd/agent        # Windows: go build -o agent.exe ./cmd/agent
./agent -config config.yaml
```

По умолчанию агент читает `config.yaml`. Не коммитьте реальные ключи — задавайте их
через переменные окружения.

---

## Конфигурация окружения

### Backend (`backend/.env`)

Файл содержит **пример** конфигурации. Значения по умолчанию безопасны только для
локального запуска; перед эксплуатацией их необходимо заменить.

| Переменная | Назначение | Пример |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | Пароль root MySQL | `rootpassword` |
| `MYSQL_DATABASE` | Имя базы | `monitoring_db` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | Пользователь приложения | `admin` / `admin_password` |
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:mysql://mysql:3306/...` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Учётные данные БД | |
| `JWT_SECRET` | Секрет подписи JWT (мин. 32 символа) | `change-me-...` |
| `JWT_EXPIRATION` | TTL access-токена, мс | `86400000` |
| `JWT_REFRESH_EXPIRATION` | TTL refresh-токена, мс | `604800000` |
| `SPRING_H2_CONSOLE_ENABLED` | Включить H2 console (только dev) | `true` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Режим схемы Hibernate | `update` |

### Frontend

| Переменная | Назначение | По умолчанию |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Базовый путь API | `/api` |
| `VITE_BACKEND_PROXY_TARGET` / `VITE_BACKEND_URL` | Куда проксировать `/api` в dev | `http://localhost:8080` |

> Значения `VITE_*` попадают в клиентский бандл и **не являются секретами**.

### Go-агент

Приоритет: переменные окружения **переопределяют** `config.yaml`.

| Источник | Параметр | Описание |
| --- | --- | --- |
| `config.yaml` / `AGENT_BACKEND_URL` | `backend_url` | Базовый URL backend (http/https) |
| `config.yaml` / `AGENT_API_KEY` | `api_key` | Scoped API-ключ приложения |
| `config.yaml` / `AGENT_INTERVAL_SECONDS` | `interval_seconds` | Интервал сбора (по умолчанию 15с) |

---

## Установка агента

Агент собирает базовые метрики хоста через `gopsutil` и отправляет их в облако по
`POST /api/v1/ingest/metrics` с заголовком `X-API-Key`.

Собираемые метрики: `system.cpu.usage`, `system.memory.usage`, `system.disk.usage`
(с тегом `host`).

### 1. Получите API-ключ

Создайте Application в веб-интерфейсе (раздел **Applications**) и скопируйте его
API-ключ. Ключ можно в любой момент перевыпустить (regenerate) — старый сразу
становится недействительным, и агент нужно перенастроить.

### 2. Соберите образ

```bash
cd agent
docker build -t vibemonitoring/agent:latest .
```

### 3. Запустите контейнер

```bash
docker run -d \
  --name monitoring-agent \
  -e AGENT_BACKEND_URL="https://your-cloud.example.com" \
  -e AGENT_API_KEY="app_your_api_key_here" \
  -e AGENT_INTERVAL_SECONDS=15 \
  vibemonitoring/agent:latest
```

Для сбора метрик контейнера/хоста может потребоваться монтирование
`/proc`, `/sys` или `--pid=host` в зависимости от окружения. Не встраивайте реальные
ключи в образы и примеры — используйте переменные окружения или secret manager.

### Поведение при сбоях

- HTTP-запросы имеют таймаут (`10s`).
- При ошибке отправки агент повторяет батч с exponential backoff (1s → 60s, до **20%**
  jitter) и не завершает процесс при недоступности backend.
- При `SIGINT`/`SIGTERM` агент корректно останавливает in-flight запросы и ожидания.

---

## Обзор API

Аутентификация пользователей — JWT (заголовок `Authorization: Bearer <token>`).
Приём телеметрии — по API-ключу (`X-API-Key`), который выдаётся на приложение.

### Auth — `/api/auth`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Регистрация | публичный |
| `POST` | `/api/auth/login` | Логин, возвращает JWT | публичный |
| `GET` | `/api/auth/me` | Текущий профиль | JWT |

### Applications — `/api/applications`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/applications` | Список приложений пользователя | JWT |
| `POST` | `/api/applications` | Создать приложение | JWT |
| `GET` | `/api/applications/{id}` | Получить приложение | JWT (владелец) |
| `PUT` | `/api/applications/{id}` | Переименовать | JWT (владелец) |
| `DELETE` | `/api/applications/{id}` | Удалить | JWT (владелец) |
| `POST` | `/api/applications/{id}/regenerate-api-key` | Перевыпустить API-ключ | JWT (владелец) |

### Metrics — `/api/v1`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `POST` | `/api/v1/ingest/metrics` | Приём батча метрик (макс. 1000 точек) | `X-API-Key` |
| `GET` | `/api/v1/metrics/query` | Агрегированный временной ряд | JWT (владелец приложения) |

Параметры `GET /api/v1/metrics/query`:
`applicationId`, `metricName`, `from`, `to` (ISO-8601 UTC), `aggregation`
(`AVG|MAX|MIN|SUM`), `intervalMinutes` (>0). Диапазон не более 30 дней.

Пример приёма метрик:

```json
POST /api/v1/ingest/metrics
X-API-Key: app_xxxxxxxx
Content-Type: application/json

{
  "metrics": [
    {
      "name": "system.cpu.usage",
      "value": 12.5,
      "timestamp": "2026-01-01T12:00:00Z",
      "tags": { "host": "server-1" }
    }
  ]
}
```

### Logs — `/api/v1`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `POST` | `/api/v1/ingest/logs` | Приём батча логов (макс. 1000 событий) | `X-API-Key` |
| `GET` | `/api/v1/logs` | Постраничный поиск логов | JWT (владелец приложения) |

Параметры `GET /api/v1/logs`: `applicationId` (обязателен), `level`
(`DEBUG|INFO|WARN|ERROR`), `from`, `to` (ISO-8601 UTC, обязательны), `searchText`
(до 200 символов), `page` (>= 0), `size` (1..100). Диапазон не более 30 дней.

Ответ имеет форму `{ content, totalElements, totalPages, page, size }` (собственный
`PageResponse`, не Spring Data `Page`); `content[]` содержит
`{ id, applicationId, timestamp, level, message, context }`.

Пример приёма логов:

```json
POST /api/v1/ingest/logs
X-API-Key: app_xxxxxxxx
Content-Type: application/json

{
  "logs": [
    {
      "timestamp": "2026-01-01T12:00:00Z",
      "level": "ERROR",
      "message": "Connection timed out",
      "context": "{\"service\":\"checkout\"}"
    }
  ]
}
```

### Dashboards — `/api/v1/dashboards`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/dashboards` | Список дашбордов | JWT (владелец) |
| `POST` | `/api/v1/dashboards` | Создать дашборд | JWT |
| `GET` | `/api/v1/dashboards/{id}` | Дашборд с виджетами | JWT (владелец) |
| `PUT` | `/api/v1/dashboards/{id}` | Переименовать | JWT (владелец) |
| `DELETE` | `/api/v1/dashboards/{id}` | Удалить | JWT (владелец) |
| `POST` | `/api/v1/dashboards/{id}/widgets` | Добавить виджет | JWT (владелец) |
| `PUT` | `/api/v1/dashboards/{id}/widgets/{widgetId}` | Обновить виджет | JWT (владелец) |
| `DELETE` | `/api/v1/dashboards/{id}/widgets/{widgetId}` | Удалить виджет | JWT (владелец) |

Виджет хранит `type` (`METRIC_CHART|LOG_TABLE|STATUS`) и `config` — JSON-строку.
Раскладка задаётся в 12-колоночной сетке (`positionX`, `positionY`, `width`,
`height`); размеры/позиции валидируются, максимум 20 виджетов на дашборд.

---

## Тесты

```bash
# Backend (JUnit 5)
cd backend && ./mvnw test            # Windows: mvnw.cmd test

# Frontend (Vitest)
cd frontend && npm test

# Frontend type-check
cd frontend && npm run typecheck
```

Тесты детерминированы и не зависят от внешних сервисов и сети: backend-тесты используют
H2, frontend-тесты — jsdom.

---

## Текущие ограничения

- **Health checks** — нет API и UI (синтетические проверки не реализованы).
- Виджет **Status** в дашбордах — плейсхолдер (зависит от health-checks API).
- Агент пока собирает только хост-метрики; сбор логов и интеграции приложений — в
  планах.
- Алерты и инциденты — в планах.
- Детали задач и статусы: `TASKS.md`.

---

## Безопасность

- **Не коммитьте** реальные ключи, токены, пароли и приватные сертификаты. В `.env` и
  `config.yaml` — только плейсхолдеры.
- `JWT_SECRET` обязателен, минимальная длина — 32 символа; меняйте значение по умолчанию
  перед публичным запуском.
- Разделяйте credentials: человеческие сессии (JWT) и агенты (`X-API-Key`). API-ключ
  приложения не даёт доступа к административным операциям или дашбордам.
- Изоляция tenant'ов: backend выводит владельца из аутентификации/ключа и проверяет
  принадлежность ресурса; скрытие данных в UI не считается авторизацией.
- Логи и телеметрия считаются недоверенными данными: не выполняются как команды/SQL,
  не рендерятся как HTML, ограничиваются по размеру.
- Не логируйте Authorization-заголовки и API-ключи (агент маскирует ключ в логах).
- Для продакшена обязателен HTTPS.
