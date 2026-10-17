# VibeMonitoring — Cloud Observability Platform

Платформа облачного мониторинга в духе Datadog/Grafana: пользователи регистрируются в
SaaS-интерфейсе, подключают свои приложения и инфраструктуру, а затем наблюдают метрики,
логи, состояние сервисов и дашборды. Телеметрию в облако отправляет устанавливаемый в
инфраструктуре клиента **Go-агент** (изначально распространяется как Docker-образ).

> Статус: ранняя стадия. Реализованы аутентификация (регистрация с подтверждением email и
> сбросом пароля), управление приложениями (Applications), конвейеры метрик и логов
> (ingest + query), дашборды с виджетами (Metric chart / Log table / Status),
> синтетические health-checks (HTTP-проверки с историей результатов), а также правила
> алертов (по метрике или health-check) с webhook-уведомлениями и историей инцидентов —
> см. раздел [Текущие ограничения](#текущие-ограничения).

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
| `monitoring-collector` | Приём метрик/логов (`/api/v1/ingest`) и запросы метрик/логов (`/api/v1/metrics/query`, `/api/v1/metrics/names`, `/api/v1/logs`) |
| `monitoring-dashboards` | CRUD дашбордов и виджетов (`/api/v1/dashboards`) |
| `monitoring-healthcheck` | Синтетические HTTP health-checks: CRUD, scheduler, SSRF-защита, история результатов (`/api/v1/health-checks`) |
| `monitoring-alerts` | Правила алертов (метрики/health-checks), плановый evaluator, webhook-уведомления (SSRF-защита) и инциденты (`/api/v1/alert-rules`, `/api/v1/notification-channels`, `/api/v1/alert-incidents`) |
| `monitoring-audit` | Аудит-лог: запись событий через AOP (`@AuditLog`) и read-only API (`/api/v1/audit-events`) |
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
- Сбор метрик хоста и tail лог-файлов (offset tracking, redaction, ротация)
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
│   ├── monitoring-healthcheck/  # синтетические health-checks
│   ├── monitoring-audit/        # аудит-лог (immutable, read-only API)
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
│   ├── cmd/agent/               # entry point (package main)
│   ├── internal/                # config, transport, metrics, logs
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
`SPRING_DATASOURCE_DRIVER_CLASS_NAME`).

**Схема БД управляется Flyway** — единственный источник истины. Миграции лежат в
`db/migration` соответствующих модулей (`V1..Vn`), Flyway собирает их с общего classpath
приложения и применяет по порядку. Hibernate работает в режиме `validate`: он проверяет,
что сущности соответствуют реальной схеме, но не изменяет её.

> Если вы ранее запускали backend без Flyway, в MySQL volume уже есть таблицы, созданные
> Hibernate. При первом запуске с Flyway возникнет ошибка «Found non-empty schema without
> schema history». Пересоздайте локальную БД один раз: `docker compose down -v`.

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
| `JWT_SECRET` | Секрет подписи JWT (обязателен, мин. 32 символа) | `change-me-...` |
| `JWT_EXPIRATION` | TTL access-токена, мс | `86400000` |
| `SPRING_H2_CONSOLE_ENABLED` | Включить H2 console (по умолчанию выключено; включайте только в dev) | `false` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Режим схемы Hibernate (схему создаёт Flyway) | `validate` |
| `APP_AUTH_VERIFICATION_URL` | Фронтенд-URL страницы подтверждения email | `http://localhost:3000/verify-email` |
| `APP_AUTH_RESET_URL` | Фронтенд-URL страницы сброса пароля | `http://localhost:3000/reset-password` |
| `APP_AUTH_VERIFICATION_TTL` | TTL токена подтверждения (ISO-8601 Duration) | `PT24H` |
| `APP_AUTH_RESET_TTL` | TTL токена сброса пароля (ISO-8601 Duration) | `PT1H` |
| `APP_AUTH_LOG_EMAILS` | Писать письма (со ссылками) в лог вместо SMTP | `false` (в профиле `dev` — `true`) |
| `MONITORING_DOWNSAMPLE_ENABLED` | Включить job сворачивания старых метрик в часовые агрегаты | `true` |
| `MONITORING_DOWNSAMPLE_CRON` | Расписание downsampling (Spring cron) | `0 5 * * * *` |
| `MONITORING_DOWNSAMPLE_RAW_RETENTION_HOURS` | Возраст сырых метрик, после которого их сворачивают, часы | `24` |
| `MONITORING_DOWNSAMPLE_BATCH_SIZE` | Размер батча удаления сырых метрик | `10000` |
| `MONITORING_RETENTION_ENABLED` | Включить job удаления устаревших логов и агрегатов | `true` |
| `MONITORING_RETENTION_CRON` | Расписание retention-очистки (Spring cron) | `0 0 2 * * *` |
| `MONITORING_RETENTION_LOGS_DAYS` | Срок хранения логов, дней | `14` |
| `MONITORING_RETENTION_METRICS_DAYS` | Срок хранения агрегатов метрик, дней | `90` |
| `MONITORING_RETENTION_BATCH_SIZE` | Размер батча удаления устаревших записей | `10000` |

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
| `config.yaml` | `logs` | Список tail-источников `{ path, service_name, level_pattern }` (только файл) |
| `config.yaml` / `AGENT_LOG_BATCH_SIZE` | `log_batch_size` | Размер батча логов, 1..1000 (по умолчанию 500) |
| `config.yaml` / `AGENT_LOG_FLUSH_SECONDS` | `log_flush_seconds` | Интервал принудительной отправки логов (по умолчанию 5с) |
| `config.yaml` / `AGENT_STATE_FILE` | `state_file` | Файл с offset'ами чтения логов (по умолчанию `.agent_state.json`) |

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

### 4. Сбор логов (опционально)

Агент tail-ит указанные файлы и отправляет новые строки в `POST /api/v1/ingest/logs`.
Источники задаются в `config.yaml` в секции `logs`:

```yaml
logs:
  - path: /var/log/my-app/app.log     # точный файл, не директория
    service_name: my-app              # необязательно; по умолчанию имя файла
    level_pattern: 'level=(\w+)'      # необязательно; 1-я группа regex = уровень
log_batch_size: 500
log_flush_seconds: 5
state_file: ".agent_state.json"
```

- При первом запуске существующий файл читается с конца (только новые строки).
- Offset каждого файла хранится в `state_file`, поэтому после перезапуска агент
  продолжает с последней позиции.
- Ротация (`app.log` → `app.log.1`, либо truncate) обрабатывается: новый файл читается
  с начала без падения агента.
- Уровень определяется по regex (`level_pattern` или встроенный:
  TRACE/DEBUG/INFO/WARN/WARNING/ERROR/FATAL/CRITICAL), иначе `INFO`.
- Время события берётся из начала строки по нескольким форматам, иначе — текущее UTC.
- Перед отправкой выполняется redaction: `password=...`, `token=...`, `api_key=...`,
  `Bearer ...`, `app_...` и AWS-ключи маскируются.

Запуск контейнера с монтированием каталога логов и writable volume для state:

```bash
docker run -d \
  --name monitoring-agent \
  -e AGENT_BACKEND_URL="https://your-cloud.example.com" \
  -e AGENT_API_KEY="app_your_api_key_here" \
  -v /var/log/my-app:/var/log/my-app:ro \
  -v /var/lib/vibemonitoring-agent:/app \
  vibemonitoring/agent:latest
```

> Каталог логов монтируется только для чтения. Для state-файла нужен writable volume на
> `/app` (каталог по умолчанию) либо задайте `AGENT_STATE_FILE` в доступный путь.

### Поведение при сбоях

- HTTP-запросы имеют таймаут (`10s`).
- При ошибке отправки агент повторяет батч с exponential backoff (1s → 60s, до **20%**
  jitter) и не завершает процесс при недоступности backend.
- Логи буферизуются и отправляются пачками (по `log_batch_size` или каждые
  `log_flush_seconds`). Буфер ограничен: при длительной недоступности backend самые
  старые события отбрасываются, чтобы не исчерпать память.
- При `SIGINT`/`SIGTERM` агент корректно останавливает in-flight запросы и ожидания,
  а также выполняет финальное чтение и отправку остатка логов (в пределах `10s`).

---

## Обзор API

Аутентификация пользователей — JWT (заголовок `Authorization: Bearer <token>`).
Приём телеметрии — по API-ключу (`X-API-Key`), который выдаётся на приложение.

### Auth — `/api/auth`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Регистрация (создаёт неподтверждённый аккаунт, письмо не логинит) | публичный |
| `POST` | `/api/auth/verify-email` | Подтвердить email по одноразовому токену | публичный |
| `POST` | `/api/auth/resend-verification` | Повторно отправить письмо подтверждения | публичный |
| `POST` | `/api/auth/forgot-password` | Запросить ссылку сброса пароля | публичный |
| `POST` | `/api/auth/reset-password` | Установить новый пароль по токену | публичный |
| `POST` | `/api/auth/login` | Логин, возвращает JWT (только для подтверждённых аккаунтов) | публичный |
| `GET` | `/api/auth/me` | Текущий профиль | JWT |

Поток подтверждения email: `register` создаёт пользователя с `emailVerified=false` и
отправляет одноразовую ссылку; до подтверждения `login` отвечает `403`. Ссылки строятся
на фронтенд-страницы `/verify-email` и `/reset-password` (параметр `token` в query).
Токены хранятся в БД только как SHA-256-хеш, одноразовые, с ограниченным TTL
(подтверждение — 24ч, сброс — 1ч); выпуск нового токена инвалидирует предыдущий того же
типа. `forgot-password` и `resend-verification` всегда отвечают успехом и не раскрывают
существование аккаунта (защита от user enumeration).

Отправка писем пока реализована как запись в лог backend: ссылка с токеном печатается в
лог приложения только когда включён `app.auth.log-emails` (по умолчанию выключено,
в профиле `dev` — включено). SMTP-провайдер ещё не подключён
(см. [Текущие ограничения](#текущие-ограничения)).

### Applications — `/api/applications`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/applications` | Список приложений пользователя | JWT |
| `POST` | `/api/applications` | Создать приложение | JWT |
| `GET` | `/api/applications/{id}` | Получить приложение | JWT (владелец) |
| `PUT` | `/api/applications/{id}` | Переименовать | JWT (владелец) |
| `DELETE` | `/api/applications/{id}` | Удалить | JWT (владелец) |
| `POST` | `/api/applications/{id}/regenerate-api-key` | Перевыпустить API-ключ | JWT (владелец) |

API-ключи хранятся только как SHA-256-хэш. Открытый ключ возвращается один раз — в ответе на
создание (`POST /api/applications`) или перевыпуск (`POST .../regenerate-api-key`); в списке и
в ответе `GET` ключ не возвращается. Старый plaintext-ключ после миграции недействителен и
требует перевыпуска.

### Metrics — `/api/v1`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `POST` | `/api/v1/ingest/metrics` | Приём батча метрик (макс. 1000 точек) | `X-API-Key` |
| `GET` | `/api/v1/metrics/query` | Агрегированный временной ряд | JWT (владелец приложения) |
| `GET` | `/api/v1/metrics/names` | Уникальные имена метрик приложения | JWT (владелец приложения) |

Параметры `GET /api/v1/metrics/query`:
`applicationId`, `metricName`, `from`, `to` (ISO-8601 UTC), `aggregation`
(`AVG|MAX|MIN|SUM`), `intervalMinutes` (>0). Диапазон не более 30 дней.

Параметры `GET /api/v1/metrics/names`: `applicationId` (обязателен). Возвращает
отсортированный по алфавиту список уникальных имён метрик (до 500), ограниченный
scope приложения, поэтому один тенант не может перечислить метрики другого.
Используется фронтендом для выбора имени метрики вместо ручного ввода.

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
      "timestamp": "<ISO-8601 UTC>",
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
  "events": [
    {
      "timestamp": "<ISO-8601 UTC>",
      "level": "ERROR",
      "message": "Connection timed out",
      "context": "{\"service\":\"checkout\"}"
    }
  ]
}
```

`timestamp` — момент события в UTC и должен попадать в запрашиваемое окно. Виджет
**Log table** запрашивает относительный диапазон («последние N часов/дней», поле Time
range в форме виджета, по умолчанию 1 час) до текущего момента, а `GET /api/v1/logs`
отдаёт только события внутри `from`..`to`. Событие с фиксированной датой из примера
(`2026-01-01`) в это окно не попадёт и в виджете не отобразится.

### Overview — `/api/v1/overview`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/overview` | Счётчики рабочего пространства пользователя | JWT |

Возвращает `{ applicationCount, metricNameCount24h, logEventCount24h }`: количество приложений
пользователя (всего) и число полученных за последние 24 часа уникальных имён метрик и событий
логов. Счётчики считаются только по приложениям вызывающего пользователя (tenant scope) тремя
агрегатными запросами, без отдельного запроса по каждому приложению. Используется карточками на
странице **Overview** (`/app/overview`).

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
Новые виджеты создаются раскрытыми (12×10 ячеек), а слишком маленький сохранённый
размер поднимается до минимума (4×6) при отображении и сохранении, чтобы графики и
таблицы не перекрывались.

На странице дашборда (`/app/dashboards/{id}`) виджеты можно перетаскивать за заголовок
и изменять размер без отдельного режима редактирования: раскладка сохраняется
автоматически по завершении перетаскивания/ресайза (`PUT .../widgets/{widgetId}`), а
кнопка **Add widget** доступна постоянно. Имя метрики в форме виджета и в Metrics
Explorer выбирается из списка: сначала идут встроенные метрики, которые собирает агент
(`system.cpu.usage`, `system.memory.usage`, `system.disk.usage`), затем имена, реально
полученные от выбранного приложения (`GET /api/v1/metrics/names`); вариант **Custom metric
name…** оставляет свободный ввод произвольного имени.

Каждой метрике соответствует свой цвет линии на графике, выбранный из палитры интерфейса
(cyan/violet/lime/amber и их светлые варианты): встроенные метрики имеют фиксированный цвет,
произвольные — детерминированный по имени. Тот же цвет показан маркером рядом с названием
метрики в списке, в заголовке графика и в заголовке виджета дашборда.

### Health checks — `/api/v1/health-checks`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/health-checks` | Список проверок пользователя | JWT (владелец) |
| `POST` | `/api/v1/health-checks` | Создать проверку | JWT |
| `PUT` | `/api/v1/health-checks/{id}` | Обновить (частично) | JWT (владелец) |
| `DELETE` | `/api/v1/health-checks/{id}` | Удалить проверку и её историю | JWT (владелец) |
| `GET` | `/api/v1/health-checks/{id}/results` | Последние результаты (`limit`, по умолчанию 50, максимум 500) | JWT (владелец) |

Конфигурация проверки: `name` (1–100), `targetUrl` (абсолютный http/https),
`method` (`GET|POST`), `expectedStatusCode` (100–599), `intervalSeconds` (≥10) и
`isActive`. Результат: `status` (`UP|DOWN|TIMEOUT|UNKNOWN`), `responseTimeMs`,
`errorMessage`, `checkedAt` (UTC). Проверка без результатов всегда `UNKNOWN`, никогда
не `UP`.

Backend выполняет проверки по расписанию (по умолчанию каждые 10 секунд, отдельный
однопоточный планировщик) и сохраняет результат; сетевой вызов выполняется вне
транзакции. Редиректы отключены. `targetUrl` проверяется на SSRF перед сохранением и
повторно перед каждым запуском: разрешены только http/https, без credentials в URL, и
хост должен разрешаться исключительно в публичные адреса (loopback, private,
link-local, CGNAT, multicast, `169.254.169.254` и т.п. отклоняются). Планировщик можно
отключить через `monitoring.healthcheck.scheduler.enabled=false`.

Страница **Health** (`/app/health`) показывает сводку (up/down/timeout/unknown), таблицу
проверок с последним результатом и автообновлением раз в 30 секунд (пауза при скрытой
вкладке), форму создания/редактирования и модальное окно истории (последние 50
результатов и uptime за 24 часа, вычисленный только по реальным результатам).

### Alerting — `/api/v1/alert-rules`, `/api/v1/notification-channels`, `/api/v1/alert-incidents`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/alert-rules` | Список правил пользователя | JWT (владелец) |
| `POST` | `/api/v1/alert-rules` | Создать правило | JWT |
| `PUT` | `/api/v1/alert-rules/{id}` | Обновить (частично) | JWT (владелец) |
| `DELETE` | `/api/v1/alert-rules/{id}` | Удалить правило и его инциденты | JWT (владелец) |
| `GET` | `/api/v1/notification-channels` | Список webhook-каналов | JWT (владелец) |
| `POST` | `/api/v1/notification-channels` | Добавить webhook | JWT |
| `PUT` | `/api/v1/notification-channels/{id}` | Обновить канал | JWT (владелец) |
| `DELETE` | `/api/v1/notification-channels/{id}` | Удалить канал | JWT (владелец) |
| `GET` | `/api/v1/alert-incidents` | История инцидентов (`limit`, по умолчанию 50, максимум 500) | JWT (владелец) |

Правило: `name` (1–100), `type` (`METRIC_THRESHOLD|HEALTH_CHECK`), `target` (имя метрики
или id health-check), `condition` (`GREATER_THAN|LESS_THAN|EQUAL`), `threshold` (double),
`durationMinutes` (1–1440) и `isActive`. Для `METRIC_THRESHOLD` обязателен `applicationId`;
для `HEALTH_CHECK` правило срабатывает при статусе `DOWN` или `TIMEOUT` (condition и
threshold игнорируются). Инцидент: `id`, `ruleId`, `ruleName`, `status`
(`FIRING|RESOLVED`), `triggeredAt`, `resolvedAt`.

Плановый evaluator (отдельный пул с ограниченной очередью и `CallerRunsPolicy`) проверяет
активные правила каждые `monitoring.alerts.scheduler-interval-ms` (по умолчанию 60 секунд).
Условие должно непрерывно держаться `durationMinutes` прежде чем откроется инцидент.
Отсутствие или устаревание данных не трактуется ни как breach, ни как recovery: метрика
ищется в окне `monitoring.alerts.metric-lookback-minutes` (по умолчанию 5), результат
health-check — не старше `monitoring.alerts.health-freshness-minutes` (по умолчанию 10).
Планировщик отключается через `monitoring.alerts.scheduler.enabled=false`.

Webhook-каналы допускают только https-URL и проходят SSRF-проверку (хост должен
разрешаться исключительно в публичные адреса) при сохранении и повторно перед каждой
отправкой; отправка выполняется асинхронно после коммита транзакции, без редиректов и с
таймаутами.

Страница **Alerts** (`/app/alerts`) показывает список правил с переключателями активности,
диалог создания/редактирования и настройку notification-каналов; страница **Incidents**
(`/app/incidents`) — таблицу инцидентов (новые первыми) с автообновлением раз в 30 секунд.

### Audit — `/api/v1/audit-events`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/audit-events` | Постраничный список событий пользователя (новые первыми) | JWT (владелец) |
| `GET` | `/api/v1/audit-events/{id}` | Одно событие (404, если не принадлежит пользователю) | JWT (владелец) |

Параметры `GET /api/v1/audit-events`: `action`, `from`, `to` (ISO-8601 UTC), `page`
(≥0, по умолчанию 0), `size` (1–100, по умолчанию 50). Диапазон `from`..`to`
включающий, должен быть положительным и не длиннее 366 дней.

Событие: `id`, `userId`, `username` (разрешается при чтении), `action`, `resourceId`,
`ipAddress`, `timestamp` (UTC). События пишутся синхронно аспектом `AuditLogAspect` при
вызове методов, помеченных `@AuditLog(action=...)`: `API_KEY_REGENERATED` (регенерация
ключа приложения), `APPLICATION_DELETED`, `DASHBOARD_DELETED`. Лог неизменяемый:
create/update/delete эндпоинтов нет, а `DELETE` на `/api/v1/audit-events` возвращает 405.
События всегда ограничены текущим пользователем (tenant scope из JWT), при определении
IP учитывается `X-Forwarded-For`.

Страница **Audit log** (`/app/audit`) показывает read-only таблицу (Timestamp, User,
Action, Resource, IP Address) с фильтрами по action и диапазону дат и постраничной
навигацией, новые события первыми.

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

- Виджет **Status** в дашбордах — плейсхолдер (ещё не связан с health-checks API).
- Письма подтверждения email и сброса пароля не отправляются по SMTP: ссылка пишется в
  лог backend, если включён `app.auth.log-emails` (по умолчанию выключено, в профиле `dev`
  включено). Для продакшена нужен реальный `EmailSender` (SMTP-провайдер) без записи
  токенов в лог.
- Агент пока собирает только хост-метрики; сбор логов и интеграции приложений — в
  планах.
- Каналы уведомлений ограничены webhook (email/Slack — в планах); правила поддерживают
  одно условие без логики AND/OR.
- Детали задач и статусы: `TASKS.md`.

---

## Безопасность

- **Не коммитьте** реальные ключи, токены, пароли и приватные сертификаты. В `.env` и
  `config.yaml` — только плейсхолдеры.
- `JWT_SECRET` обязателен: без него приложение не стартует (нет небезопасного значения по
  умолчанию), минимальная длина — 32 символа. Задавайте его через окружение.
- Небезопасные для продакшена вещи выключены по умолчанию и включаются только в профиле `dev`:
  H2 console (`SPRING_H2_CONSOLE_ENABLED=false`), вывод SQL (`spring.jpa.show-sql=false`) и
  детали Actuator health (`management.endpoint.health.show-details=never`).
- Разделяйте credentials: человеческие сессии (JWT) и агенты (`X-API-Key`). API-ключ
  приложения не даёт доступа к административным операциям или дашбордам.
- Изоляция tenant'ов: backend выводит владельца из аутентификации/ключа и проверяет
  принадлежность ресурса; скрытие данных в UI не считается авторизацией.
- Логи и телеметрия считаются недоверенными данными: не выполняются как команды/SQL,
  не рендерятся как HTML, ограничиваются по размеру.
- Не логируйте Authorization-заголовки и API-ключи (агент маскирует ключ в логах).
- Токены подтверждения email и сброса пароля хранятся в БД только как SHA-256-хеш,
  одноразовые и с ограниченным TTL. В лог токен (в составе ссылки) попадает только когда
  явно включён `app.auth.log-emails` (в профиле `dev` включён, в продакшене выключен по
  умолчанию — включать не следует).
- Для продакшена обязателен HTTPS.
