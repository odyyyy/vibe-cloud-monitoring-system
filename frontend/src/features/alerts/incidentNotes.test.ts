import { describe, expect, it } from 'vitest'
import { MAX_INCIDENT_NOTE_LENGTH, validateIncidentNote } from './incidentNotes'

describe('validateIncidentNote', () => {
  it('accepts plain text and empty notes', () => {
    expect(validateIncidentNote('')).toBeNull()
    expect(validateIncidentNote('Root cause: cache stampede & retry storm.')).toBeNull()
  })

  it('rejects HTML markup', () => {
    expect(validateIncidentNote('<script>alert(1)</script>')).not.toBeNull()
    expect(validateIncidentNote('a < b')).not.toBeNull()
  })

  it('rejects notes above the backend limit', () => {
    expect(validateIncidentNote('a'.repeat(MAX_INCIDENT_NOTE_LENGTH))).toBeNull()
    expect(validateIncidentNote('a'.repeat(MAX_INCIDENT_NOTE_LENGTH + 1))).not.toBeNull()
  })
})
