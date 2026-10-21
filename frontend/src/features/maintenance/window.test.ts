import { describe, expect, it } from 'vitest'
import type { MaintenanceWindow } from '../../api/maintenanceWindows'
import {
  activeMaintenanceWindows,
  isMaintenanceWindowActive,
  maintenanceScopeLabel,
  maintenanceWindowStatus,
} from './window'

const NOW = new Date('2026-10-21T12:00:00Z')

function window(overrides: Partial<MaintenanceWindow> = {}): MaintenanceWindow {
  return {
    id: 1,
    organizationId: 1,
    name: 'Deploy',
    scopeType: 'GLOBAL',
    scopeId: null,
    startTime: '2026-10-21T11:00:00Z',
    endTime: '2026-10-21T13:00:00Z',
    isActive: true,
    createdAt: '2026-10-21T10:00:00Z',
    updatedAt: '2026-10-21T10:00:00Z',
    ...overrides,
  }
}

describe('maintenanceWindowStatus', () => {
  it('classifies a window containing the instant as active', () => {
    expect(maintenanceWindowStatus(window(), NOW)).toBe('ACTIVE')
  })

  it('classifies a future window as upcoming', () => {
    expect(
      maintenanceWindowStatus(
        window({ startTime: '2026-10-21T13:00:00Z', endTime: '2026-10-21T14:00:00Z' }),
        NOW,
      ),
    ).toBe('UPCOMING')
  })

  it('treats the end instant as ended', () => {
    expect(
      maintenanceWindowStatus(
        window({ startTime: '2026-10-21T10:00:00Z', endTime: '2026-10-21T12:00:00Z' }),
        NOW,
      ),
    ).toBe('ENDED')
  })

  it('reports a manually disabled window as disabled even inside its time range', () => {
    expect(maintenanceWindowStatus(window({ isActive: false }), NOW)).toBe('DISABLED')
  })
})

describe('activeMaintenanceWindows', () => {
  it('keeps only active windows', () => {
    const active = window({ id: 1 })
    const upcoming = window({ id: 2, startTime: '2026-10-21T13:00:00Z' })
    const disabled = window({ id: 3, isActive: false })

    const result = activeMaintenanceWindows([active, upcoming, disabled], NOW)

    expect(result.map((entry) => entry.id)).toEqual([1])
  })

  it('reports active status consistently', () => {
    expect(isMaintenanceWindowActive(window(), NOW)).toBe(true)
    expect(isMaintenanceWindowActive(window({ isActive: false }), NOW)).toBe(false)
  })
})

describe('maintenanceScopeLabel', () => {
  const applications = new Map([[7, 'web']])
  const rules = new Map([[11, 'High CPU']])

  it('describes each scope type', () => {
    expect(maintenanceScopeLabel(window(), applications, rules)).toBe('All alerts')
    expect(
      maintenanceScopeLabel(window({ scopeType: 'APPLICATION', scopeId: 7 }), applications, rules),
    ).toBe('web')
    expect(
      maintenanceScopeLabel(window({ scopeType: 'SPECIFIC_RULE', scopeId: 11 }), applications, rules),
    ).toBe('High CPU')
  })

  it('falls back to the resource id for unreferenced scopes', () => {
    expect(
      maintenanceScopeLabel(window({ scopeType: 'APPLICATION', scopeId: 99 }), applications, rules),
    ).toBe('Application #99')
  })
})
