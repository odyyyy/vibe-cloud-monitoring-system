export const MAX_INCIDENT_NOTE_LENGTH = 1000

/**
 * Validates incident notes before sending them to the API.
 *
 * <p>Notes are plain text: markup is rejected rather than rendered. The backend enforces the
 * same rules, so this is a fast client-side guard, not the security boundary.
 *
 * @returns an error message, or {@code null} when the value is valid (including empty).
 */
export function validateIncidentNote(value: string): string | null {
  if (value.length > MAX_INCIDENT_NOTE_LENGTH) {
    return `Notes must be at most ${MAX_INCIDENT_NOTE_LENGTH} characters.`
  }
  if (value.includes('<') || value.includes('>')) {
    return 'Notes must be plain text without HTML.'
  }
  return null
}
