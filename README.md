# VibeMonitoring — Cloud Observability Platform

Платформа облачного мониторинга в духе Datadog/Grafana: пользователи регистрируются в
SaaS-интерфейсе, подключают свои приложения и инфраструктуру, а затем наблюдают метрики,
логи, состояние сервисов и дашборды. Телеметрию в облако отправляет устанавливаемый в
инфраструктуре клиента **Go-агент** (изначально распространяется как Docker-образ).

> Статус: ранняя стадия. Реализованы аутентификация (регистрация с подтверждением email и
> сбросом пароля), управление приложениями (Applications), конвейеры метрик и логов
> (ingest + query), дашборды с виджетами (Metric chart / Log table / Status),
> синтетические health-checks (HTTP-проверки с историей результатов), правила
> алертов (по метрике или health-check, с несколькими условиями AND/OR) с уведомлениями в
> webhook/Slack/email и историей инцидентов,
> а также настройки профиля (смена username/email с подтверждением и три темы оформления) —
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
| `monitoring-auth` | Пользователи (identity), регистрация/логин, JWT, Spring Security; организации, членство и роли, tenant isolation |
| `monitoring-apps-management` | CRUD приложений и управление API-ключами |
| `monitoring-collector` | Приём метрик/логов (`/api/v1/ingest`) и запросы метрик/логов (`/api/v1/metrics/query`, `/api/v1/metrics/names`, `/api/v1/logs`), сохранённые представления логов (`/api/v1/saved-log-views`), учёт потребления и квот (`/api/v1/usage`) |
| `monitoring-dashboards` | CRUD дашбордов и виджетов (`/api/v1/dashboards`) |
| `monitoring-healthcheck` | Синтетические HTTP health-checks: CRUD, scheduler, SSRF-защита, история результатов (`/api/v1/health-checks`) |
| `monitoring-alerts` | Правила алертов (метрики/health-checks) с составными условиями AND/OR, плановый evaluator, уведомления webhook/Slack/email (SSRF-защита, rate limit) и инциденты (`/api/v1/alert-rules`, `/api/v1/notification-channels`, `/api/v1/alert-incidents`) |
| `monitoring-audit` | Аудит-лог: запись событий через AOP (`@AuditLog`) и read-only API (`/api/v1/audit-events`) |
| `monitoring-common` | Общие типы без домена: пагинация (`PageResponse`), тело ошибки (`ApiError`) |
| `monitoring-api` | Точка входа, конфигурация, композиция модулей, Actuator, OpenAPI |

Соглашения по пакетам:

- DTO каждого модуля лежат в `dto/request` и `dto/response`.
- Домены не смешиваются в одном пакете: identity — `com.monitoring.auth`, организации — `com.monitoring.organization`.
- Доменные события размещаются в отдельном пакете `event`.

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
│   ├── monitoring-common/       # общие DTO/ошибки без доменных зависимостей
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
│   ├── internal/                # config, transport, metrics, logs, receiver
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
| `APP_AUTH_EMAIL_CHANGE_URL` | Фронтенд-URL страницы подтверждения смены email | `http://localhost:3000/confirm-email-change` |
| `APP_AUTH_VERIFICATION_TTL` | TTL токена подтверждения (ISO-8601 Duration) | `PT24H` |
| `APP_AUTH_RESET_TTL` | TTL токена сброса пароля (ISO-8601 Duration) | `PT1H` |
| `APP_AUTH_EMAIL_CHANGE_TTL` | TTL токена смены email (ISO-8601 Duration) | `PT1H` |
| `APP_AUTH_LOG_EMAILS` | Писать письма (со ссылками) в лог вместо SMTP | `false` (в профиле `dev` — `true`) |
| `APP_SSO_ENABLED` | Включить вход через OIDC-провайдера | `false` |
| `APP_SSO_ISSUER_URI` | Issuer OIDC-провайдера (HTTPS; `http` допускается только для localhost) | |
| `APP_SSO_CLIENT_ID` / `APP_SSO_CLIENT_SECRET` | Учётные данные OIDC-клиента | |
| `APP_SSO_REGISTRATION_ID` | Идентификатор регистрации (часть URL входа) | `sso` |
| `APP_SSO_ROLE_CLAIM` | Claim с группами пользователя | `groups` |
| `APP_SSO_DEFAULT_ROLE` | Роль при отсутствии совпадения групп | `VIEWER` |
| `APP_SSO_SUCCESS_REDIRECT_URL` | Куда вернуть пользователя после успешного входа | `http://localhost:3000/sso/callback` |
| `APP_SSO_FAILURE_REDIRECT_URL` | Куда вернуть пользователя при ошибке входа | `http://localhost:3000/login` |
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
| `config.yaml` | `logs` | Список tail-источников `{ path, service_name, level_pattern, format }` (только файл) |
| `config.yaml` / `AGENT_LOG_BATCH_SIZE` | `log_batch_size` | Размер батча логов, 1..1000 (по умолчанию 500) |
| `config.yaml` / `AGENT_LOG_FLUSH_SECONDS` | `log_flush_seconds` | Интервал принудительной отправки логов (по умолчанию 5с) |
| `config.yaml` / `AGENT_STATE_FILE` | `state_file` | Файл с offset'ами чтения логов (по умолчанию `.agent_state.json`) |
| `config.yaml` / `AGENT_KUBERNETES_ENABLED` | `kubernetes.enabled` | Включить автообнаружение логов подов (по умолчанию `false`) |
| `config.yaml` / `AGENT_KUBERNETES_NAMESPACE` | `kubernetes.namespace` | Namespace или `all`; пусто — все namespace |
| `config.yaml` / `AGENT_KUBERNETES_LABEL_SELECTOR` | `kubernetes.label_selector` | Селектор подов, например `app=api` |
| `config.yaml` / `AGENT_RECEIVER_ENABLED` | `receiver.enabled` | Включить локальный приём custom-метрик (по умолчанию `false`) |
| `config.yaml` / `AGENT_RECEIVER_PROTOCOL` | `receiver.protocol` | `statsd` (UDP) или `http` |
| `config.yaml` / `AGENT_RECEIVER_PORT` | `receiver.port` | Порт receiver'а (по умолчанию 8125 для StatsD, 8080 для HTTP) |
| env `NODE_NAME` | — | Ограничить discovery подами этого узла; DaemonSet задаёт автоматически |

---

## Установка агента

Агент собирает базовые метрики хоста через `gopsutil` и отправляет их в облако по
`POST /api/v1/ingest/metrics` с заголовком `X-API-Key`.

Собираемые метрики (с тегами `host` и `version`): `system.cpu.usage`,
`system.memory.usage`, `system.disk.usage`, `system.load.1`, `system.load.5`,
`system.load.15`, `system.uptime`, а также сетевые счётчики
`system.network.bytes_sent`, `system.network.bytes_recv`,
`system.network.packets_sent`, `system.network.packets_recv`,
`system.network.drop_in`, `system.network.drop_out` с тегом `interface`
(например, `interface="eth0"`). Тег `host` используется для отображения агента в разделе
**Agent fleet**, `version` — для показа версии сборки агента.

По умолчанию агент отчитывается только по физическим интерфейсам (`eth*`, `ens*`,
`enp*`, `wlan*`) и пропускает loopback и виртуальные/bridge-интерфейсы (`lo`,
`docker0`, `veth*`, `br-*`). Секция `metrics.network.interfaces` в `config.yaml`
задаёт явный список интерфейсов. Сетевые метрики отправляются как абсолютные
счётчики — скорости (байты/с, пакеты/с) вычисляет backend при запросе.

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
    format: auto                      # auto (по умолчанию), json или text
log_batch_size: 500
log_flush_seconds: 5
state_file: ".agent_state.json"
```

- При первом запуске существующий файл читается с конца (только новые строки).
- Offset каждого файла хранится в `state_file`, поэтому после перезапуска агент
  продолжает с последней позиции.
- Ротация (`app.log` → `app.log.1`, либо truncate) обрабатывается: новый файл читается
  с начала без падения агента.
- `format: auto` (по умолчанию) распознаёт JSON-объекты и извлекает стандартные поля:
  `timestamp`/`time`/`ts`/`@timestamp`, `level`/`severity`/`lvl`, `message`/`msg`.
  Остальные ключи попадают в `context`; метаданные агента (`host`, `service`, `source`,
  `k8s.*`) всегда имеют приоритет и не могут быть перезаписаны содержимым лога.
  Строки, которые не являются JSON-объектом, обрабатываются как обычный текст.
- `format: json` принудительно пытается разобрать JSON, но также откатывается к тексту
  для некорректных строк; `format: text` отключает JSON-разбор.
- Уровень определяется по regex (`level_pattern` или встроенный:
  TRACE/DEBUG/INFO/WARN/WARNING/ERROR/FATAL/CRITICAL), иначе `INFO`.
- Время события берётся из стандартного JSON-поля или из начала строки по нескольким
  форматам, иначе — текущее UTC.
- JSON-контекст ограничен глубиной вложенности 3 уровня и 4 KiB сериализованного JSON;
  лишние ключи отбрасываются.
- Перед отправкой выполняется redaction: `password=...`, `token=...`, `api_key=...`,
  `Bearer ...`, `app_...` и AWS-ключи маскируются (в том числе внутри JSON `context`).

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

### 5. Kubernetes: автообнаружение логов подов (опционально)

В Kubernetes агент запускается как DaemonSet, сам находит поды на своём узле и
tail-ит их контейнерные логи из `/var/log/containers`. В контекст каждой строки
добавляются `k8s.pod.name`, `k8s.namespace` и `k8s.container.name`.

```yaml
kubernetes:
  enabled: true
  namespace: ""                # пусто или "all" — все namespace
  label_selector: "app=api"    # необязательно; labelSelector тоже принимается
```

Что делает агент:

- Следит за подами через Kubernetes API (informer) и реагирует на появление,
  рестарт и удаление контейнеров.
- Если задан `NODE_NAME` (DaemonSet передаёт его через Downward API), watch
  ограничивается только подами этого узла (`spec.nodeName`).
- Строит путь `/var/log/containers/<pod>_<namespace>_<container>-<containerID>.log`
  и проверяет, что он не выходит за пределы `/var/log/containers`; другие файлы
  агент не читает.
- При удалении пода прекращает tail-ить его файлы и удаляет offset из state-файла.
  Если runtime ещё не назначил `containerID`, источник добавится при следующем
  обновлении пода.
- Требуются только `get`, `list`, `watch` на `pods` — без доступа к секретам,
  ConfigMap и без выполнения команд в подах.

Готовые манифесты: `agent/k8s/rbac.yaml` (Namespace, ServiceAccount, ClusterRole,
ClusterRoleBinding) и `agent/k8s/daemonset.yaml` (ConfigMap и DaemonSet; Secret
создаётся отдельно). Оба файла также входят в образ в каталоге `/app/k8s`.

```bash
cd agent
docker build -t vibemonitoring/agent:latest .
kubectl apply -f k8s/rbac.yaml

# До применения DaemonSet: реальный ключ только в Secret, не в манифесте.
kubectl -n vibemonitoring create secret generic vibemonitoring-agent \
  --from-literal=api-key="$AGENT_API_KEY"

kubectl apply -f k8s/daemonset.yaml
```

Перед применением замените `AGENT_BACKEND_URL` в `daemonset.yaml` на адрес вашего
backend. API-ключ в ConfigMap — только placeholder; фактическое значение приходит
из Secret через `AGENT_API_KEY`.

Важно:

- API-ключ передаётся только через `AGENT_API_KEY` из Secret и не хранится в образе.
- Файлы в `/var/log/containers` — symlink'и в `/var/log/pods`, поэтому оба каталога
  монтируются read-only. Код агента при этом открывает только пути внутри
  `/var/log/containers`.
- Для одного namespace замените `ClusterRole`/`ClusterRoleBinding` на
  `Role`/`RoleBinding` в этом namespace.
- Вне кластера (локально) агент использует `KUBECONFIG` или `~/.kube/config`.

### 6. Локальный приём custom-метрик (StatsD / HTTP)

Агент может принимать бизнес-метрики от приложения (например, `orders_processed`,
`login_failures`, `queue_depth`) и отправлять их в облако вместе с host-метриками
в рамках обычного интервала сбора. Receiver выключен по умолчанию:

```yaml
receiver:
  enabled: true
  protocol: statsd   # statsd (UDP) или http
  port: 8125         # по умолчанию 8125 для statsd, 8080 для http
```

```bash
# Включение через переменные окружения (port зависит от protocol)
AGENT_RECEIVER_ENABLED=true AGENT_RECEIVER_PROTOCOL=statsd AGENT_RECEIVER_PORT=8125 \
  ./agent -config config.yaml
```

Receiver **всегда** слушает только `127.0.0.1` — порт недоступен из внешней сети.
Все полученные точки получают теги `host` (hostname агента), `version` (версия
агента) и `source=custom`. `applicationId` определяется backend'ом по API-ключу,
поэтому в payload его передавать не нужно.

#### StatsD (UDP)

Поддерживается стандартный формат
`metric.name:value|type|@sample_rate|#tag1,tag2`:

```bash
# counter: значения суммируются за интервал сбора
printf 'orders_processed:1|c\n' | nc -u -w0 127.0.0.1 8125

# gauge: сохраняется последнее значение за интервал
printf 'queue.depth:42|g\n' | nc -u -w0 127.0.0.1 8125

# timer/histogram: raw-значения пересылаются как есть
printf 'request.latency:12.5|ms|#env:prod\n' | nc -u -w0 127.0.0.1 8125
```

Типы: `c` — counter (учитывается sample rate), `g` — gauge (абсолютное значение),
`ms`/`h` — timer/histogram. Теги задаются как `#env:prod,region:eu`; допустимы и
теги без значения (`#env`). Некорректные строки пропускаются, агент не падает.

Пример на Python:

```python
import socket

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(b"orders_processed:1|c|#env:prod,service:checkout", ("127.0.0.1", 8125))
```

#### HTTP

При `protocol: http` агент принимает `POST /metrics` с тем же JSON, что и
`/api/v1/ingest/metrics`:

```bash
curl -sS -X POST http://127.0.0.1:8080/metrics \
  -H 'Content-Type: application/json' \
  -d '{
    "metrics": [
      {
        "name": "orders_processed",
        "value": 1,
        "timestamp": "2026-10-20T12:00:00Z",
        "tags": {"env": "prod", "service": "checkout"}
      }
    ]
  }'
```

`timestamp` можно не указывать — агент подставит текущее UTC-время. За один запрос
принимается не более 1000 точек и не более 1 MiB. Ответы: `202` — точки приняты
(возможно, часть отброшена из-за лимита), `400` — некорректный payload, `429` —
превышен локальный лимит. Количество принятых/отброшенных точек возвращается в
заголовках `X-Metrics-Accepted` и `X-Metrics-Dropped`.

Ограничения receiver'а: не более **10 000 метрик/с** и ограниченный буфер
(50 000 точек). Лишние точки отбрасываются, чтобы локальное приложение не могло
исчерпать память агента. При переполнении буфера приоритет отдаётся новым данным.

> В Docker/Kubernetes receiver слушает loopback **внутри** контейнера агента. Чтобы
> приложение могло отправить метрику на `127.0.0.1`, оно должно находиться в том же
> network namespace: sidecar-контейнер в одном Pod'е либо
> `docker run --network container:<agent-container>`.

### Поведение при сбоях

- HTTP-запросы имеют таймаут (`10s`).
- При ошибке отправки агент повторяет батч с exponential backoff (1s → 60s, до **20%**
  jitter) и не завершает процесс при недоступности backend.
- Логи буферизуются и отправляются пачками (по `log_batch_size` или каждые
  `log_flush_seconds`). Буфер ограничен: при длительной недоступности backend самые
  старые события отбрасываются, чтобы не исчерпать память.
- Custom-метрики буферизуются до следующего цикла сбора и ограничены 10 000
  метрик/с и 50 000 точками; превышение лимита приводит к отбрасыванию точек, а не
  к росту памяти или падению агента.
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
| `POST` | `/api/auth/email-change/confirm` | Подтвердить новый email по одноразовому токену | публичный |
| `POST` | `/api/auth/login` | Логин по username или email, возвращает JWT (только для подтверждённых аккаунтов) | публичный |
| `GET` | `/api/auth/me` | Текущий профиль | JWT |
| `GET` | `/api/auth/sso/login` | Начать вход через OIDC-провайдера (`302`) | публичный |

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

#### SSO / OIDC

Вход через внешний identity provider (Keycloak, Okta, Auth0 и т.п.) выключен по умолчанию:
при `APP_SSO_ENABLED=false` эндпоинт `GET /api/auth/sso/login` отвечает `404`, а платформа
не зависит от внешнего провайдера. При включении задайте `issuer-uri`, `client-id`,
`client-secret`; issuer должен использовать HTTPS (исключение — localhost для разработки).

Поток входа:

1. Фронтенд (`Sign in with SSO` на странице логина) переходит на `GET /api/auth/sso/login`.
2. Backend отвечает `302` на `/oauth2/authorization/{registration-id}` и далее на провайдера.
3. После аутентификации backend находит или создаёт пользователя по email (JIT-provisioning),
   отмечает email подтверждённым и сопоставляет группы из claim `role-claim` с ролью
   платформы. Маппинг строгий и задаётся в конфигурации (`app.sso.role-mapping`), при
   отсутствии совпадения применяется `default-role` (`VIEWER`); при нескольких совпадениях
   выигрывает более привилегированная роль.
4. Роль применяется к личной организации пользователя (единственный `OWNER` не понижается).
5. Backend выпускает обычный внутренний JWT и перенаправляет браузер на
   `success-redirect-url` со токеном во фрагменте URL (`#token=...`); сессия OAuth2
   инвалидируется. Фронтенд-страница `/sso/callback` читает токен, сохраняет его в
   `authStore` и переходит на `/app`.

IDP-токены не сохраняются — используются только для извлечения идентичности и групп.
Локальный вход по паролю продолжает работать для остальных пользователей. SAML 2.0 и
UI для управления маппингом ролей вне scope.

### Profile — `/api/profile`

Управление собственной учётной записью. Владелец всегда определяется из JWT (тело запроса
не может указать чужого пользователя). Смена username и email требует текущего пароля.

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `PUT` | `/api/profile/username` | Сменить username (`username`, `currentPassword`) | JWT |
| `GET` | `/api/profile/username-available` | Проверить доступность username (`username`) | JWT |
| `PUT` | `/api/profile/email` | Запросить смену email (`newEmail`, `currentPassword`) | JWT |

`GET /api/profile/username-available` возвращает `{ "available": boolean }`. Ответ
успешной смены username/email — обновлённый профиль
(`id`, `username`, `email`, `avatarUrl`, `role`). Занятый username/email или совпадение с
текущим email → `409`.

Смена email двухфазная: `PUT /api/profile/email` создаёт одноразовый токен типа
`EMAIL_CHANGE` (хранит новый адрес, TTL — 1 час) и отправляет письмо со ссылкой на
фронтенд-страницу `/confirm-email-change?token=...`. Новый адрес применяется только после
`POST /api/auth/email-change/confirm`; до этого вход по-прежнему возможен по старому адресу.
На старый адрес дополнительно уходит уведомление о запросе смены. Уникальность нового email
перепроверяется в момент подтверждения. Ссылка для смены email: `app.auth.email-change-url`
(по умолчанию `http://localhost:3000/confirm-email-change`), TTL — `app.auth.email-change-ttl`
(по умолчанию `PT1H`).

### Applications — `/api/applications`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/applications` | Список приложений пользователя | JWT |
| `POST` | `/api/applications` | Создать приложение | JWT |
| `GET` | `/api/applications/{id}` | Получить приложение | JWT (владелец) |
| `PUT` | `/api/applications/{id}` | Переименовать | JWT (владелец) |
| `DELETE` | `/api/applications/{id}` | Удалить | JWT (владелец) |
| `POST` | `/api/applications/{id}/regenerate-api-key` | Перевыпустить API-ключ | JWT (владелец) |
| `GET` | `/api/applications/{id}/agents` | Агенты (fleet) приложения | JWT (владелец) |

API-ключи хранятся только как SHA-256-хэш. Открытый ключ возвращается один раз — в ответе на
создание (`POST /api/applications`) или перевыпуск (`POST .../regenerate-api-key`); в списке и
в ответе `GET` ключ не возвращается. Старый plaintext-ключ после миграции недействителен и
требует перевыпуска.

`GET /api/applications/{id}/agents` возвращает агентов, которые присылали телеметрию для
приложения. Запись агента заводится по тегу `host` при приёме метрик; `agentVersion` берётся из
тега `version` (может отсутствовать), `lastSeenAt` — момент последнего принятого батча (UTC).
Online/Offline вычисляется на фронтенде по `lastSeenAt` (порог — 5 минут). IP-адреса агентов не
хранятся и не отдаются. Перевыпуск API-ключа (`regenerate-api-key`) обесточивает все агенты
приложения до их перенастройки.

Страница **Agent fleet** (`/app/agents`) показывает таблицу агентов выбранного приложения
(Hostname, Version, Status, Last seen) с автообновлением раз в 30 секунд, кнопку
**Revoke API key** (через диалог подтверждения, с предупреждением об отключении агентов) и
действие **View agent logs**, которое открывает Log Explorer (`/app/logs`) с
предзаполненными `applicationId`, диапазоном времени за последний час и `searchText` = hostname.
Поиск в логах ищет только по тексту сообщения, а колонки `host`/`service` в логах не
индексируются, поэтому фильтр по агенту — best-effort.

### Metrics — `/api/v1`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `POST` | `/api/v1/ingest/metrics` | Приём батча метрик (макс. 1000 точек) | `X-API-Key` |
| `GET` | `/api/v1/metrics/query` | Агрегированный временной ряд | JWT (владелец приложения) |
| `GET` | `/api/v1/metrics/names` | Уникальные имена метрик приложения | JWT (владелец приложения) |

Параметры `GET /api/v1/metrics/query`:
`applicationId`, `metricName`, `from`, `to` (ISO-8601 UTC), `aggregation`
(`AVG|MAX|MIN|SUM`), `intervalMinutes` (>0). Диапазон не более 30 дней.
Необязательный `format=csv|json` превращает ответ в скачиваемый файл (см. раздел
«Экспорт логов и метрик»).

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

### Экспорт логов и метрик

`GET /api/v1/logs` и `GET /api/v1/metrics/query` принимают необязательный параметр
`format=csv|json`. Без него контракт ответа не меняется. С параметром сервер возвращает
файл с заголовком `Content-Disposition: attachment` (`export.csv` или `export.json`):

- `format=csv` — `Content-Type: text/csv; charset=UTF-8`. Каждая ячейка в кавычках,
  внутренние кавычки удвоены, поэтому запятые и переводы строк в сообщениях логов не
  ломают файл. Текстовые ячейки, начинающиеся с `=`, `+`, `-`, `@`, таба или CR,
  получают префикс `'` для защиты от формул в Excel/Numbers.
- `format=json` — массив объектов; для логов это
  `[{ id, applicationId, timestamp, level, message, context }]`, для метрик —
  `[{ timestamp, value, group }]`.

Экспорт ограничен 10 000 строками. Заголовки `X-Export-Limit`, `X-Export-Total` и
`X-Export-Truncated` позволяют фронтенду предупредить, что результат усечён. Права
доступа и tenant isolation те же, что у обычных query-эндпоинтов: `format` не ослабляет
проверки владельца приложения и организации.

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

### Saved log views — `/api/v1/saved-log-views`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/saved-log-views` | Список сохранённых представлений | JWT (владелец) |
| `POST` | `/api/v1/saved-log-views` | Сохранить текущие фильтры | JWT |
| `PUT` | `/api/v1/saved-log-views/{id}` | Переименовать | JWT (владелец) |
| `DELETE` | `/api/v1/saved-log-views/{id}` | Удалить | JWT (владелец) |

Сохранённое представление хранит имя (до 100 символов) и `queryParams` — проверенный
фильтр вида `{ applicationId, level?, searchText?, timeRange: { from, to } }`. Этот JSON
перепроверяется при каждой записи: приложение должно принадлежать владельцу, `from < to`,
диапазон не более 30 дней, `searchText` до 200 символов и без символов `<`/`>`; на одного
пользователя — не более 50 представлений (иначе HTTP 409). Пагинация в представление не
сохраняется. В Log Explorer (`/app/logs`) панель **Saved views** позволяет применить
представление одним нажатием, сохранить текущие фильтры, переименовать и удалить.

> В текущей модели представления привязаны к владельцу (`owner_id`), как и дашборды,
> алерты и health checks. Когда будет включён полноценный tenant-контур организаций,
> привязку следует перевести на `organizationId` через `CurrentTenantService`.

### Overview — `/api/v1/overview`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/overview` | Счётчики рабочего пространства пользователя | JWT |

Возвращает `{ applicationCount, metricNameCount24h, logEventCount24h }`: количество приложений
пользователя (всего) и число полученных за последние 24 часа уникальных имён метрик и событий
логов. Счётчики считаются только по приложениям вызывающего пользователя (tenant scope) тремя
агрегатными запросами, без отдельного запроса по каждому приложению. Используется карточками на
странице **Overview** (`/app/overview`).

### Usage — `/api/v1/usage`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/usage/current-month` | Текущее потребление и лимиты за календарный месяц (UTC) | JWT |
| `GET` | `/api/v1/usage/daily` | Дневное потребление за окно (`days`, по умолчанию 30, максимум 90) | JWT |

Область (organization) берётся из активного тенанта (`X-Organization-Id` + членство), поэтому
чужое потребление через этот эндпоинт получить нельзя. Потребление считается фоновым rollup-джобом
(`UsageRollupScheduler`, ежечасно по умолчанию): он пересчитывает абсолютные значения за сутки по
каждой организации в таблице `daily_usage` с уникальным ключом `(organization_id, usage_date)`,
поэтому повторный запуск идемпотентен и не может задвоить счётчики. Метрики считаются как сырые
`metric_samples` за сутки плюс сохранённый объём (`sample_count`) агрегатов, логи — по
`log_events`, трейсы — по `trace_spans`; границы суток — UTC. Лимиты задаются в
`monitoring.usage.*` (метрики/логи/трейсы в месяц) и отдаются в поле `limit`; `percentage`
рассчитывается по `used`/`limit` (на бэкенде, а также на странице для устойчивости к
округлению), поэтому малая, но ненулевая доля не превращается в `0%`. Используется страницей
**Usage** (`/app/usage`), где прогресс-бары
подсвечиваются по порогам (зелёный < 70%, жёлтый 70–90%, красный ≥ 90%) и рисуется график
дневного потребления.

### Dashboards — `/api/v1/dashboards`

| Метод | Путь | Описание | Доступ |
| --- | --- | --- | --- |
| `GET` | `/api/v1/dashboards` | Список дашбордов | JWT (владелец) |
| `POST` | `/api/v1/dashboards` | Создать дашборд | JWT |
| `GET` | `/api/v1/dashboards/{id}` | Дашборд с виджетами | JWT (владелец) |
| `PUT` | `/api/v1/dashboards/{id}` | Переименовать и/или сохранить переменные | JWT (владелец) |
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
(`system.cpu.usage`, `system.memory.usage`, `system.disk.usage`, `system.load.1`,
`system.load.5`, `system.load.15`, `system.uptime`, `system.network.*`), затем имена,
реально полученные от выбранного приложения (`GET /api/v1/metrics/names`); вариант
**Custom metric name…** оставляет свободный ввод произвольного имени.

Каждой метрике соответствует свой цвет линии на графике, выбранный из палитры интерфейса
(accent, status и chart-шкалы): встроенные метрики имеют фиксированный цвет,
произвольные — детерминированный по имени. Тот же цвет показан маркером рядом с названием
метрики в списке, в заголовке графика и в заголовке виджета дашборда.

Дашборд может определять шаблонные переменные (Grafana-style, до 20 штук): у переменной есть
`name` (только латиница, цифры и `_`, максимум 64 символа), `type` (`custom` или `query`),
`options` (1–50 значений) и `defaultValue` (пустое значение допускается). Определения сохраняются
вместе с дашбордом (`PUT /api/v1/dashboards/{id}`, поле `variables`), а выбранные в интерфейсе
значения — клиентское состояние и не персистятся. Панель переменных отображается вверху страницы
дашборда, а выбранное значение подставляется в конфиг виджета простой заменой `${name}` (в
`metricName` графиков и `searchText` логов) до выполнения запроса, поэтому смена значения мгновенно
перезапрашивает все использующие её виджеты. Неразрешённые (неопределённые или пустые)
плейсхолдеры остаются как есть — данные не подменяются молча. Подстановка — обычная строковая
замена без `eval` и исполнения выражений; редактор переменных доступен кнопкой **Variables**
(роли ADMIN/OWNER). Динамические `query`-переменные (загрузка опций с бэкенда) и связанные
переменные не реализованы.

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
| `GET` | `/api/v1/notification-channels` | Список каналов (webhook/Slack/email) | JWT (владелец) |
| `POST` | `/api/v1/notification-channels` | Добавить канал | JWT |
| `PUT` | `/api/v1/notification-channels/{id}` | Обновить канал | JWT (владелец) |
| `DELETE` | `/api/v1/notification-channels/{id}` | Удалить канал | JWT (владелец) |
| `POST` | `/api/v1/notification-channels/{id}/test` | Отправить тестовое уведомление (async, `202 Accepted`) | JWT (владелец) |
| `GET` | `/api/v1/alert-incidents` | История инцидентов (`limit`, по умолчанию 50, максимум 500) | JWT (владелец) |

Правило: `name` (1–100), `type` (`METRIC_THRESHOLD|HEALTH_CHECK`), список `conditions`
(до 10; каждая — `target`, `operator` и `threshold`), `logicOperator` (`AND|OR`),
`durationMinutes` (1–1440) и `isActive`. Для `METRIC_THRESHOLD` обязателен `applicationId`;
для `HEALTH_CHECK` правило срабатывает при статусе `DOWN` или `TIMEOUT` (одно условие —
id health-check). Устаревшие поля `target`/`condition`/`threshold` по-прежнему принимаются
(одно условие) и в ответе зеркалят первое условие. Инцидент: `id`, `ruleId`, `ruleName`,
`status` (`FIRING|RESOLVED`), `triggeredAt`, `resolvedAt`.

Плановый evaluator (отдельный пул с ограниченной очередью и `CallerRunsPolicy`) проверяет
активные правила каждые `monitoring.alerts.scheduler-interval-ms` (по умолчанию 60 секунд).
Составное условие вычисляется как `AND` (все условия breach) или `OR` (любое условие breach);
результат должен непрерывно держаться `durationMinutes` прежде чем откроется инцидент.
Отсутствие или устаревание данных хотя бы по одному условию не трактуется ни как breach,
ни как recovery: метрика ищется в окне `monitoring.alerts.metric-lookback-minutes` (по
умолчанию 5), результат health-check — не старше `monitoring.alerts.health-freshness-minutes`
(по умолчанию 10). Планировщик отключается через `monitoring.alerts.scheduler.enabled=false`.

Каналы уведомлений бывают трёх типов: `WEBHOOK` (произвольный https-URL), `SLACK` (incoming
webhook на хосте `slack.com`, сообщение в формате Block Kit) и `EMAIL` (адрес получателя).
Webhook- и Slack-URL допускают только https и проходят SSRF-проверку (хост должен разрешаться
исключительно в публичные адреса) при сохранении и повторно перед каждой отправкой. Все
отправки выполняются асинхронно после коммита транзакции, без редиректов и с таймаутами;
email использует `spring-boot-starter-mail` (нужен `spring.mail.host`) и отдельный пул
`monitoring.alerts.email.*`, поэтому недоступный SMTP не блокирует evaluation. Повторные
уведомления по одному правилу и каналу ограничены `monitoring.alerts.notifications.min-interval-seconds`
(по умолчанию 900). Тестовое сообщение в канал отправляется через
`POST /api/v1/notification-channels/{id}/test`.

Страница **Alerts** (`/app/alerts`) показывает список правил с переключателями активности,
диалог создания/редактирования (несколько условий с переключателем AND/OR) и настройку
notification-каналов (webhook/Slack/email с кнопкой тестовой отправки); страница **Incidents**
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

### Онбординг — Setup guide

Для нового пользователя (нет приложений или за последние 24 часа не приходило ни метрик,
ни логов) автоматически открывается мастер настройки из 4 шагов:

1. **Create application** — создание первой Application (встроенная форма).
2. **Install the agent** — готовый `docker run` с подставленными backend URL и API-ключом
   и кнопкой **Copy**. URL и ключ экранируются по правилам POSIX single-quote; если
   plaintext-ключ больше не доступен, команда содержит плейсхолдер и кнопку
   **Regenerate key**.
3. **Verify connection** — мастер опрашивает
   `GET /api/v1/applications/{id}/agents` каждые 4 секунды и ждёт агента со свежим
   `lastSeenAt`. Через 2 минуты ожидание останавливается (без бессрочного спиннера),
   доступны «Check now» и повтор правил диагностики.
4. **Success** — экран успеха с переходом к метрикам этой Application или к обзору.

Пройденный или закрытый мастер больше не открывается автоматически: статус хранится в
`localStorage` (`onboarding:v1:<userId>`), отдельно для каждого пользователя. Вернуться к
мастеру можно кнопкой **Setup guide** в сайдбаре.

### Settings — `/app/settings`

Страница настроек аккаунта позволяет:

- **Сменить тему оформления.** Доступны три темы — **Midnight** (тёмная, по умолчанию),
  **Daylight** (светлая, чёрно-белая) и **Aurora** (тёмная).

  **Daylight** использует белые поверхности и монохромные акценты в оттенках почти-чёрного:
  нейтральная палитра (`slate`, оверлеи `white/*`) и светлые оттенки статусных цветов
  инвертируются через CSS-переменные, поэтому текст и границы остаются читаемыми без правок
  каждого компонента. Выбор сохраняется в `localStorage` этого устройства (ключ
  `theme`) и не синхронизируется между устройствами. Темы реализованы через CSS-переменные
  (`--c-*` в `src/index.css`) и атрибут `data-theme` на `<html>`; палитра подключена в
  `tailwind.config.js`.
- **Сменить username.** Форма проверяет доступность имени по мере ввода (debounce, запрос к
  `GET /api/profile/username-available`) и требует текущий пароль. После успеха стор
  обновляется без повторного логина (JWT аутентифицирует по `uid`, а не по username).
- **Сменить email.** Ввод нового адреса и текущего пароля; платформа отправляет письмо со
  ссылкой подтверждения (см. [Profile](#profile--apiprofile)). Результат подтверждается на
  странице `/confirm-email-change`.

Форма «Change password» на этой странице — пока превью (эндпоинт не реализован).

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
- Каналы уведомлений поддерживают webhook, Slack и email. Отправка email требует
  настроенного SMTP (`spring.mail.host`); пока он не задан, канал сохраняется, но доставка
  не выполняется (ошибка логируется). Правила поддерживают несколько условий с логикой AND/OR.
- Тема оформления перекрашивает интерфейс (поверхности, нейтральный текст, акценты и статусы),
  но серии графиков (Recharts), маркетинговый лендинг и изображения используют фиксированные
  цвета. В светлой теме **Daylight** акценты монохромные, поэтому разные серии на графике
  могут различаться слабо.
- Смена username/email не пишется в аудит-лог: модуль `monitoring-audit` зависит от
  `monitoring-auth`, поэтому обратный `@AuditLog` создал бы циклическую зависимость.
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
