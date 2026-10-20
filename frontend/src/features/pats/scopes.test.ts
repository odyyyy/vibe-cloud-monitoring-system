import { describe, expect, it } from 'vitest'
import { groupedPatScopes, isPatScope, PAT_SCOPES, PAT_SCOPE_VALUES, patScopeLabel } from './scopes'

describe('pat scopes catalog', () => {
  it('lists every scope exactly once', () => {
    const values = PAT_SCOPES.map((scope) => scope.value)
    expect(new Set(values).size).toBe(values.length)
    expect(new Set(values)).toEqual(new Set(PAT_SCOPE_VALUES))
  })

  it('groups all scopes without loss or duplication', () => {
    const grouped = groupedPatScopes().flatMap((entry) => entry.scopes.map((scope) => scope.value))
    expect(grouped.sort()).toEqual([...PAT_SCOPE_VALUES].sort())
  })

  it('recognises only known scopes', () => {
    expect(isPatScope('read:metrics')).toBe(true)
    expect(isPatScope('read:everything')).toBe(false)
    expect(isPatScope('')).toBe(false)
  })

  it('provides a human label and falls back to the raw value', () => {
    expect(patScopeLabel('write:dashboards')).toBe('Write dashboards')
    expect(patScopeLabel('unknown:scope')).toBe('unknown:scope')
  })
})
