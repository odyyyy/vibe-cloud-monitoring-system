#!/usr/bin/env bash
# Restore a logical mysqldump backup into the monitoring database.
#
# Credentials are read from environment variables (or from .env next to this repository).
# By default the restore runs inside the running Compose "mysql" service. The target
# database must exist and should be empty: this script does not drop or recreate it.
#
# Usage:
#   scripts/restore-db.sh --yes backups/monitoring_db-20260101T000000Z.sql
#   DB_HOST=127.0.0.1 DB_PORT=3306 scripts/restore-db.sh --yes /tmp/monitoring.sql
set -euo pipefail
umask 077

ASSUME_YES=0
if [[ "${1:-}" == "--yes" ]]; then
    ASSUME_YES=1
    shift
fi

INPUT="${1:-}"
if [[ -z "${INPUT}" || ! -f "${INPUT}" ]]; then
    echo "usage: $0 [--yes] <backup-file.sql>" >&2
    exit 1
fi

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

if [[ -z "${DB_NAME}" || -z "${DB_USER}" || -z "${DB_PASSWORD}" ]]; then
    echo "error: DB_NAME, DB_USER and DB_PASSWORD (or MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD) are required" >&2
    exit 1
fi

if [[ "${ASSUME_YES}" -ne 1 ]]; then
    if [[ ! -t 0 ]]; then
        echo "error: refusing to restore without confirmation; pass --yes in a non-interactive shell" >&2
        exit 1
    fi
    read -r -p "Restore '${INPUT}' into '${DB_NAME}'? Type 'restore' to continue: " answer
    if [[ "${answer}" != "restore" ]]; then
        echo "Aborted."
        exit 1
    fi
fi

echo "Restoring ${INPUT} into database '${DB_NAME}' ..."

if [[ -n "${DB_HOST:-}" ]]; then
    if ! command -v mysql >/dev/null 2>&1; then
        echo "error: mysql client is not installed on this host" >&2
        exit 1
    fi
    MYSQL_PWD="${DB_PASSWORD}" mysql \
        --host="${DB_HOST}" \
        --port="${DB_PORT:-3306}" \
        --user="${DB_USER}" \
        --default-character-set=utf8mb4 \
        "${DB_NAME}" < "${INPUT}"
else
    if ! docker compose ps --services --filter status=running 2>/dev/null | tr -d '\r' | grep -qx mysql; then
        echo "error: the Compose 'mysql' service is not running." >&2
        echo "       Start the stack with 'docker compose up -d', or set DB_HOST to use a local client." >&2
        exit 1
    fi
    # The first stdin line is the password; the remaining stream is the SQL dump.
    {
        printf '%s\n' "${DB_PASSWORD}"
        cat "${INPUT}"
    } | docker compose exec -T mysql sh -c '
        IFS= read -r MYSQL_PWD || exit 1
        export MYSQL_PWD
        exec mysql --user="$1" --default-character-set=utf8mb4 "$2"
    ' sh "${DB_USER}" "${DB_NAME}"
fi

echo "Restore completed."
