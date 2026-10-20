/**
 * Catalog of scopes a Personal Access Token can be granted.
 *
 * <p>Keep this list in sync with the backend allowlist in
 * {@code com.monitoring.auth.security.PatScopes}. The UI only offers these values; the backend
 * rejects anything else, and a token can never exceed the role its owner holds.
 */
export const PAT_SCOPE_VALUES = [
  'read:metrics',
  'read:logs',
  'read:traces',
  'read:dashboards',
  'write:dashboards',
  'read:health',
  'write:health',
  'read:alerts',
  'write:alerts',
  'read:applications',
  'write:applications',
  'read:overview',
  'read:usage',
  'read:audit',
] as const

export type PatScope = (typeof PAT_SCOPE_VALUES)[number]

export interface PatScopeDefinition {
  value: PatScope
  label: string
  description: string
  group: string
}

export const PAT_SCOPES: readonly PatScopeDefinition[] = [
  { value: 'read:metrics', label: 'Read metrics', description: 'Query metrics and metric names.', group: 'Telemetry' },
  { value: 'read:logs', label: 'Read logs', description: 'Search and filter log events.', group: 'Telemetry' },
  { value: 'read:traces', label: 'Read traces', description: 'Query distributed traces.', group: 'Telemetry' },
  {
    value: 'read:dashboards',
    label: 'Read dashboards',
    description: 'View dashboards and their widgets.',
    group: 'Dashboards',
  },
  {
    value: 'write:dashboards',
    label: 'Write dashboards',
    description: 'Create, update and delete dashboards.',
    group: 'Dashboards',
  },
  {
    value: 'read:health',
    label: 'Read health checks',
    description: 'View synthetic checks and their results.',
    group: 'Health',
  },
  {
    value: 'write:health',
    label: 'Write health checks',
    description: 'Create, update and delete synthetic checks.',
    group: 'Health',
  },
  {
    value: 'read:alerts',
    label: 'Read alerts',
    description: 'View alert rules, incidents and channels.',
    group: 'Alerts',
  },
  {
    value: 'write:alerts',
    label: 'Write alerts',
    description: 'Manage alert rules, incidents and channels.',
    group: 'Alerts',
  },
  {
    value: 'read:applications',
    label: 'Read applications',
    description: 'List applications and their agents.',
    group: 'Applications',
  },
  {
    value: 'write:applications',
    label: 'Write applications',
    description: 'Create, update and delete applications.',
    group: 'Applications',
  },
  {
    value: 'read:overview',
    label: 'Read overview',
    description: 'Read the workspace overview.',
    group: 'Account',
  },
  { value: 'read:usage', label: 'Read usage', description: 'Read ingestion usage reports.', group: 'Account' },
  { value: 'read:audit', label: 'Read audit log', description: 'Read the organization audit trail.', group: 'Account' },
]

export const PAT_SCOPE_GROUP_ORDER = ['Telemetry', 'Dashboards', 'Health', 'Alerts', 'Applications', 'Account'] as const

const PAT_SCOPE_SET: ReadonlySet<string> = new Set(PAT_SCOPE_VALUES)

export function isPatScope(value: string): value is PatScope {
  return PAT_SCOPE_SET.has(value)
}

export function patScopeLabel(value: string): string {
  return PAT_SCOPES.find((scope) => scope.value === value)?.label ?? value
}

export interface PatScopeGroup {
  group: string
  scopes: PatScopeDefinition[]
}

/** Groups the catalog for display, in a stable order. */
export function groupedPatScopes(): PatScopeGroup[] {
  return PAT_SCOPE_GROUP_ORDER.map((group) => ({
    group,
    scopes: PAT_SCOPES.filter((scope) => scope.group === group),
  })).filter((entry) => entry.scopes.length > 0)
}
