import { describe, expect, it } from 'vitest'
import { componentStatusPresentation, overallStatusPresentation } from './status'

describe('status page presentation', () => {
  it('maps every overall status to a distinct label and variant', () => {
    expect(overallStatusPresentation('UP')).toEqual({
      label: 'All Systems Operational',
      variant: 'success',
    })
    expect(overallStatusPresentation('DEGRADED')).toEqual({
      label: 'Partial Outage',
      variant: 'warning',
    })
    expect(overallStatusPresentation('DOWN')).toEqual({
      label: 'Major Outage',
      variant: 'danger',
    })
    expect(overallStatusPresentation('UNKNOWN')).toEqual({
      label: 'Status Unknown',
      variant: 'muted',
    })
  })

  it('maps component statuses without implying missing data is healthy', () => {
    expect(componentStatusPresentation('UP').variant).toBe('success')
    expect(componentStatusPresentation('DEGRADED').variant).toBe('warning')
    expect(componentStatusPresentation('DOWN').variant).toBe('danger')
    expect(componentStatusPresentation('UNKNOWN')).toEqual({ label: 'Unknown', variant: 'muted' })
  })
})
