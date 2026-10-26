import { matchRoutes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { router } from '../../App'
import type { ResourceSearchResult, ResourceSearchType } from '../../api/search'
import { FEATURE_ENTRIES } from './registry'
import { resourceRoute } from './resourceRoutes'

const RESOURCE_TYPES: ResourceSearchType[] = [
  'applications',
  'dashboards',
  'alertRules',
  'healthChecks',
  'savedViews',
  'incidents',
]

/** True when the path resolves to a real route, not the catch-all redirect. */
function hasRoute(route: string): boolean {
  const pathname = route.split('#')[0]
  const matches = matchRoutes(router.routes, pathname)
  return matches !== null && matches.some((match) => match.route.path !== '*')
}

describe('command palette route drift', () => {
  it('every feature entry points at an existing route', () => {
    for (const entry of FEATURE_ENTRIES) {
      expect(hasRoute(entry.route), `${entry.id} -> ${entry.route}`).toBe(true)
    }
  })

  it('every resource type maps to an existing route', () => {
    for (const type of RESOURCE_TYPES) {
      const result: ResourceSearchResult = {
        type,
        id: 42,
        title: 'Sample resource',
        subtitle: '',
      }
      expect(hasRoute(resourceRoute(result)), `${type} -> ${resourceRoute(result)}`).toBe(true)
    }
  })

  it('does not treat unknown app paths as valid', () => {
    expect(hasRoute('/app/traces')).toBe(false)
  })
})
