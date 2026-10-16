const HAS_TIMEZONE = /(Z|[+-]\d{2}:?\d{2})$/i

/**
 * Parses a backend timestamp as a UTC instant.
 *
 * The backend serializes `LocalDateTime` values without a timezone designator
 * (e.g. `2024-05-01T10:15:30`). Those values are stored as UTC, so a trailing
 * `Z` is appended before parsing to prevent the browser from interpreting them
 * in the local timezone. Values that already carry an offset are used as-is.
 */
export function parseUtcInstant(value: string): Date | null {
  if (!value) {
    return null
  }
  const normalized = HAS_TIMEZONE.test(value) ? value : `${value}Z`
  const parsed = new Date(normalized)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/** Formats a backend timestamp for display, converting from UTC to local time. */
export function formatDateTime(value: string): string {
  const date = parseUtcInstant(value)
  if (!date) {
    return value
  }
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}
