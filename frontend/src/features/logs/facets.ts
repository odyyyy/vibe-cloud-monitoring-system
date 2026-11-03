import type { LogPipelineRule } from '../../api/logPipelines'
import type { LogFacetField, LogLevel, LogsQueryParams } from '../../api/logs'
import { LOG_FACET_FIELDS } from '../../api/logs'

/** The backend accepts at most eight facet fields per request. */
export const MAX_FACET_FIELDS = 8

export const FACET_FIELD_LABELS: Partial<Record<LogFacetField, string>> = {
  level: 'Level',
  host: 'Host',
  service: 'Service',
  'k8s.namespace': 'Namespace',
}

/** The built-in fields the sidebar always requests. */
export const REQUESTED_FACET_FIELDS: readonly LogFacetField[] = LOG_FACET_FIELDS

/** The facets the sidebar always renders; others are only shown when they have values. */
export const ALWAYS_VISIBLE_FACET_FIELDS: readonly LogFacetField[] = ['level', 'host', 'service']

export function facetLabel(field: LogFacetField): string {
  const builtIn = FACET_FIELD_LABELS[field]
  if (builtIn) {
    return builtIn
  }
  if (field.startsWith('context.')) {
    return field.slice('context.'.length)
  }
  return field
}

export function isDynamicFacetField(field: LogFacetField): boolean {
  return field.startsWith('context.')
}

/**
 * Facet fields captured by PARSE_REGEX rules that apply to the selected application. The list is
 * bounded so the built-in fields plus captured fields never exceed the backend limit.
 */
export function buildRequestedFacetFields(
  rules: readonly LogPipelineRule[] | undefined,
  applicationId: number | null,
): LogFacetField[] {
  const fields: LogFacetField[] = [...REQUESTED_FACET_FIELDS]
  if (rules) {
    for (const rule of rules) {
      if (rule.action !== 'PARSE_REGEX') {
        continue
      }
      if (rule.applicationId !== null && rule.applicationId !== applicationId) {
        continue
      }
      for (const extracted of rule.extractedFields) {
        const field = `context.${extracted}` as LogFacetField
        if (!fields.includes(field) && fields.length < MAX_FACET_FIELDS) {
          fields.push(field)
        }
      }
    }
  }
  return fields
}

/**
 * The filter the explorer applies for a facet value, or {@code null} when the field has no
 * exact-match filter of its own.
 */
export function facetFilterFor(params: LogsQueryParams, field: LogFacetField): string | undefined {
  switch (field) {
    case 'level':
      return params.level
    case 'host':
      return params.host
    case 'service':
      return params.service
    case 'k8s.namespace':
      return params.namespace
    default:
      if (isDynamicFacetField(field) && params.contextKey === field.slice('context.'.length)) {
        return params.contextValue
      }
      return undefined
  }
}

export function isFacetValueActive(
  params: LogsQueryParams,
  field: LogFacetField,
  value: string,
): boolean {
  return facetFilterFor(params, field) === value
}

/** True when any context facet filter is active; drives the "Clear" action of the sidebar. */
export function hasActiveFacetFilters(params: LogsQueryParams): boolean {
  return Boolean(params.host || params.service || params.namespace || params.contextKey)
}

/**
 * Adds the exact-match filter for a facet value, or removes it when the value is already active.
 * The page is reset because the result set changes.
 */
export function toggleFacetFilter(
  params: LogsQueryParams,
  field: LogFacetField,
  value: string,
): LogsQueryParams {
  const next: LogsQueryParams = { ...params, page: 0 }
  const active = isFacetValueActive(params, field, value)

  switch (field) {
    case 'level':
      if (active) {
        delete next.level
      } else if (isLogLevel(value)) {
        next.level = value
      }
      return next
    case 'host':
      return toggleContextFilter(next, 'host', active, value)
    case 'service':
      return toggleContextFilter(next, 'service', active, value)
    case 'k8s.namespace':
      return toggleContextFilter(next, 'namespace', active, value)
    default:
      if (!isDynamicFacetField(field)) {
        return next
      }
      if (active) {
        delete next.contextKey
        delete next.contextValue
      } else {
        next.contextKey = field.slice('context.'.length)
        next.contextValue = value
      }
      return next
  }
}

function toggleContextFilter(
  next: LogsQueryParams,
  key: 'host' | 'service' | 'namespace',
  active: boolean,
  value: string,
): LogsQueryParams {
  if (active) {
    delete next[key]
  } else {
    next[key] = value
  }
  return next
}

/** Removes every context facet filter and resets the page. */
export function clearFacetFilters(params: LogsQueryParams): LogsQueryParams {
  const next: LogsQueryParams = { ...params, page: 0 }
  delete next.host
  delete next.service
  delete next.namespace
  delete next.contextKey
  delete next.contextValue
  return next
}

/** Copies the context facet filters of a committed query onto a freshly built query. */
export function carryOverFacetFilters(
  source: LogsQueryParams | null,
  target: LogsQueryParams,
): LogsQueryParams {
  if (source === null) {
    return target
  }
  const next: LogsQueryParams = { ...target }
  if (source.host) {
    next.host = source.host
  }
  if (source.service) {
    next.service = source.service
  }
  if (source.namespace) {
    next.namespace = source.namespace
  }
  if (source.contextKey && source.contextValue) {
    next.contextKey = source.contextKey
    next.contextValue = source.contextValue
  }
  return next
}

function isLogLevel(value: string): value is LogLevel {
  return value === 'DEBUG' || value === 'INFO' || value === 'WARN' || value === 'ERROR'
}
