import { describe, expect, it } from 'vitest'
import {
  MAX_INCIDENT_COMMENT_LENGTH,
  parseIncidentComment,
  validateIncidentComment,
} from './incidentComments'

describe('parseIncidentComment', () => {
  it('turns dashboard references into internal links', () => {
    const segments = parseIncidentComment('Check dashboard:456 now')

    expect(segments).toEqual([
      { type: 'text', value: 'Check ' },
      { type: 'dashboard', dashboardId: 456, value: 'dashboard:456' },
      { type: 'text', value: ' now' },
    ])
  })

  it('accepts slash- and at-prefixed dashboard references', () => {
    expect(parseIncidentComment('/dashboard:12')).toContainEqual({
      type: 'dashboard',
      dashboardId: 12,
      value: 'dashboard:12',
    })
    expect(parseIncidentComment('@dashboard:789')).toEqual([
      { type: 'dashboard', dashboardId: 789, value: 'dashboard:789' },
    ])
  })

  it('does not link words that merely end in dashboard', () => {
    expect(parseIncidentComment('mydashboard:1')).toEqual([
      { type: 'text', value: 'mydashboard:1' },
    ])
  })

  it('parses basic markdown and safe external links', () => {
    const segments = parseIncidentComment('**CPU** `95%` [runbook](https://example.com/run)')

    expect(segments).toEqual([
      { type: 'strong', value: 'CPU' },
      { type: 'text', value: ' ' },
      { type: 'code', value: '95%' },
      { type: 'text', value: ' ' },
      { type: 'link', href: 'https://example.com/run', value: 'runbook' },
    ])
  })

  it('leaves raw HTML and unsafe links as text', () => {
    const segments = parseIncidentComment('<img src=x onerror=alert(1)> [x](javascript:alert(1))')

    expect(segments).toEqual([
      {
        type: 'text',
        value: '<img src=x onerror=alert(1)> [x](javascript:alert(1))',
      },
    ])
  })
})

describe('validateIncidentComment', () => {
  it('rejects blank comments', () => {
    expect(validateIncidentComment('   ')).not.toBeNull()
  })

  it('rejects overlong comments', () => {
    expect(validateIncidentComment('x'.repeat(MAX_INCIDENT_COMMENT_LENGTH + 1))).not.toBeNull()
  })

  it('accepts a normal comment', () => {
    expect(validateIncidentComment('Investigating now, looks like DB CPU is high.')).toBeNull()
  })
})
