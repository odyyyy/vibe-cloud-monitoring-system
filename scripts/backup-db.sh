#!/usr/bin/env bash
# Create a logical mysqldump backup of the monitoring database.
#
# Credentials are read from environment variables (or from .env next to this repository).
# They are never hardcoded in this script. By default the dump runs inside the running
# Compose "mysql" service, so MySQL does not need to be published on the host.
#
# Usage:
#   scripts/backup-db.sh [output-file]
#   DB_HOST=127.0.0.1 DB_PORT=3306 scripts/backup-db.sh /tmp/monitoring.sql
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

# Parse .env without executing it: values may contain characters such as &, spaces or
# quotes that are unsafe in a shell "source". Variables already set in the environment win.
load_env_file() {
    [[ -f .env ]] || return 0
    local line key value
    while IFS= read -r line || [[ -n "${line}" ]]; do
        line="${line%$'\r'}"
        [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
        [[ "${line}" == *=* ]] || continue
        key="${line%%=*}"
        value="${line#*=}"
        key="$(printf '%s' "${key}" | tr -d '[:space:]')"
        [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
        if [[ "${value}" == \"*\" || "${value}" == \'*\' ]]; then
            value="${value:1:${#value}-2}"
        fi
        if [[ -z "${!key:-}" ]]; then
            export "${key}=${value}"
        fi
    done < .env
}
load_env_file

DB_NAME="${DB_NAME:-${MYSQL_DATABASE:-}}"
DB_USER="${DB_USER:-${MYSQL_USER:-}}"
DB_PASSWORD="${DB_PASSWORD:-${MYSQL_PASSWORD:-}}"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT="${1:-${BACKUP_DIR}/${DB_NAME}-${TIMESTAMP}.sql}"

if [[ -z "${DB_NAME}" || -z "${DB_USER}" || -z "${DB_PASSWORD}" ]]; then
    echo "error: DB_NAME, DB_USER and DB_PASSWORD (or MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD) are required" >&2
    exit 1
fi

DUMP_ARGS=(
    --single-transaction
    --quick
    --routines
    --triggers
    --no-tablespaces
    --default-character-set=utf8mb4
)

mkdir -p "$(dirname "${OUTPUT}")"
TMP_OUTPUT="${OUTPUT}.tmp"
trap 'rm -f "${TMP_OUTPUT}"' EXIT

echo "Backing up database '${DB_NAME}' to ${OUTPUT} ..."

if [[ -n "${DB_HOST:-}" ]]; then
    if ! command -v mysqldump >/dev/null 2>&1; then
        echo "error: mysqldump is not installed on this host" >&2
        exit 1
    fi
    MYSQL_PWD="${DB_PASSWORD}" mysqldump \
        --host="${DB_HOST}" \
        --port="${DB_PORT:-3306}" \
        --user="${DB_USER}" \
        "${DUMP_ARGS[@]}" \
        "${DB_NAME}" > "${TMP_OUTPUT}"
else
    if ! docker compose ps --services --filter status=running 2>/dev/null | tr -d '\r' | grep -qx mysql; then
        echo "error: the Compose 'mysql' service is not running." >&2
        echo "       Start the stack with 'docker compose up -d', or set DB_HOST to use a local mysqldump." >&2
        exit 1
    fi
    # The password is read from stdin so it does not appear in the container command line.
    printf '%s\n' "${DB_PASSWORD}" | docker compose exec -T mysql sh -c '
        IFS= read -r MYSQL_PWD || exit 1
        export MYSQL_PWD
        exec mysqldump --user="$1" --single-transaction --quick --routines --triggers \
            --no-tablespaces --default-character-set=utf8mb4 "$2"
    ' sh "${DB_USER}" "${DB_NAME}" > "${TMP_OUTPUT}"
fi

if [[ ! -s "${TMP_OUTPUT}" ]]; then
    echo "error: the backup is empty; check the database credentials and logs" >&2
    exit 1
fi

mv "${TMP_OUTPUT}" "${OUTPUT}"
trap - EXIT
echo "Backup completed: ${OUTPUT}"
