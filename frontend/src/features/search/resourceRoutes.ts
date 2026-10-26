import type { LucideIcon } from 'lucide-react'
import {
  Activity,
  Bell,
  Bookmark,
  Boxes,
  Gauge,
  Siren,
} from 'lucide-react'
import type { ResourceSearchResult, ResourceSearchType } from '../../api/search'

export const RESOURCE_TYPE_ORDER: ResourceSearchType[] = [
  'applications',
  'dashboards',
  'alertRules',
  'healthChecks',
  'savedViews',
  'incidents',
]

export const RESOURCE_TYPE_LABELS: Record<ResourceSearchType, string> = {
  applications: 'Applications',
  dashboards: 'Dashboards',
  alertRules: 'Alert rules',
  healthChecks: 'Health checks',
  savedViews: 'Saved views',
  incidents: 'Incidents',
}

export const RESOURCE_TYPE_ICONS: Record<ResourceSearchType, LucideIcon> = {
  applications: Boxes,
  dashboards: Gauge,
  alertRules: Bell,
  healthChecks: Activity,
  savedViews: Bookmark,
  incidents: Siren,
}

/**
 * Maps a resource result to the page that actually exists today.
 *
 * <p>Only dashboards have a dedicated detail route; the other resources are managed on their
 * list pages. Do not invent `/applications/:id`-style routes that the router does not serve.
 */
export function resourceRoute(result: ResourceSearchResult): string {
  switch (result.type) {
    case 'dashboards':
      return `/app/dashboards/${result.id}`
    case 'applications':
      return '/app/applications'
    case 'alertRules':
      return '/app/alerts'
    case 'healthChecks':
      return '/app/health'
    case 'savedViews':
      return '/app/logs'
    case 'incidents':
      return '/app/incidents'
  }
}

export function groupResourceResults(results: ResourceSearchResult[]): Array<{
  type: ResourceSearchType
  results: ResourceSearchResult[]
}> {
  return RESOURCE_TYPE_ORDER
    .map((type) => ({
      type,
      results: results.filter((result) => result.type === type),
    }))
    .filter((group) => group.results.length > 0)
}
