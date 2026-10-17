import { parseUtcInstant } from '../../lib/datetime'

/**
 * Converts a `<input type="datetime-local">` value (local wall-clock time, e.g.
 * `2024-05-01T10:15`) into a UTC ISO instant for the API. Returns `''` when empty or
 * unparseable, so callers can treat "no value" and "invalid value" uniformly.
 */
export function localInputToInstant(value: string): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toISOString()
}

/**
 * Converts a backend UTC instant into the local wall-clock string expected by a
 * `<input type="datetime-local">`, or `''` when the value is empty or unparseable.
 */
export function instantToLocalInput(value: string): string {
  const date = parseUtcInstant(value)
  if (!date) {
    return ''
  }
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
