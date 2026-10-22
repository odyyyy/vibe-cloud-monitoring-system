# Operations

This document covers the production-like Compose stack, health probes, backups and the
security rules that apply to deployment packaging.

## Stack layout

`compose.yaml` starts three services:

| Service | Purpose | Host port |
| --- | --- | --- |
| `mysql` | MySQL 8 with a named volume (`mysql-data`) | not published |
| `backend` | Spring Boot API, readiness probe includes DB connectivity | `BACKEND_PORT` (default 8080) |
| `frontend` | React build served by unprivileged nginx; proxies `/api` to the backend | `FRONTEND_PORT` (default 3000) |

An optional `agent` profile runs the Go agent inside the Compose network.

`compose.dev.yaml` is an override for `compose.yaml`, not a separate stack. It publishes
MySQL on the host, replaces the nginx frontend with the Vite dev server and enables Compose
watch for the backend and agent:

```bash
docker compose -f compose.yaml -f compose.dev.yaml up --build --watch
```

Both files read the root `.env`. MySQL is only published by the development override.

## Configuration and secrets

Copy `.env.example` to `.env`; `.env` is ignored by git. The backend runs without the
`prod` profile by default so the local quick start works with the example values.

With `SPRING_PROFILES_ACTIVE=prod` the backend validates required secrets at startup:

- `JWT_SECRET` must be set, at least 32 bytes, and must not be an example value.
- Invalid configuration stops the process with a clear `Refusing to start...` message.

Generate secrets with `openssl rand -base64 48`. Keep database credentials in
`MYSQL_*` and `SPRING_DATASOURCE_*` in sync. SSO credentials are empty and SSO is disabled
by default.

No separate application-level encryption key exists in the current release. The JWT secret
is the only signing key; application API keys are stored as SHA-256 hashes.

## Health probes

The backend exposes only `health` and `prometheus` Actuator endpoints:

| Endpoint | Meaning |
| --- | --- |
| `/actuator/health/liveness` | Process is alive; does not depend on MySQL |
| `/actuator/health/readiness` | Ready to serve traffic; includes `db` connectivity |
| `/actuator/prometheus` | Prometheus metrics for scraping |

The Compose healthcheck and the image `HEALTHCHECK` use `/actuator/health/readiness`.
Stopping MySQL therefore moves the readiness probe to `DOWN` (HTTP 503) and, after the
configured retries, marks the backend container `unhealthy`. Starting MySQL again restores
both without restarting the backend.

The frontend nginx container only proxies `/api/`. It never proxies `/actuator/*`, so the
Prometheus endpoint is not reachable through the UI origin. Scrape it from the backend
network or explicitly from the published backend port if that port is exposed.

## Backup and restore

The scripts use `mysqldump` and `mysql`, read credentials from `.env` or the environment,
and never hardcode passwords. Without `DB_HOST` they run inside the Compose `mysql`
service, so the database does not need a host port.

```bash
# Dump to backups/monitoring_db-<UTC timestamp>.sql
scripts/backup-db.sh

# Explicit output path
scripts/backup-db.sh /var/backups/monitoring.sql

# Restore into an existing empty database
scripts/restore-db.sh --yes backups/monitoring_db-20260101T000000Z.sql
```

To use a locally installed client against a published or remote database:

```bash
DB_HOST=127.0.0.1 DB_PORT=3306 scripts/backup-db.sh
```

The restore script does not drop or recreate a database. Create an empty database (or
drop/recreate `MYSQL_DATABASE`) before restoring. Backups contain customer telemetry and
credential hashes: store them encrypted, restrict access and delete them according to the
retention policy. The repository ignores `backups/`, but do not rely on that alone.

## Local quick start

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

The first registered account can be verified with the link written to the backend log
(`APP_AUTH_LOG_EMAILS=true` in the example file). To grant `PLATFORM_ADMIN`, register the
account, set `MONITORING_PLATFORM_ADMIN_USERNAME` in `.env`, and restart the backend (the
role is also re-checked periodically).

To run the agent against the stack, create an Application in the UI, copy its API key into
`AGENT_API_KEY` in `.env`, then:

```bash
docker compose --profile agent up -d agent
```
