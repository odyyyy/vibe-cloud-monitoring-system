import type { LogFacetField, LogLevel, LogsQueryParams } from '../../api/logs'
import { LOG_FACET_FIELDS } from '../../api/logs'

export const FACET_FIELD_LABELS: Record<LogFacetField, string> = {
  level: 'Level',
  host: 'Host',
  service: 'Service',
  'k8s.namespace': 'Namespace',
}

/**
 * The fields requested from the facet endpoint. The backend accepts at most four, and asking for
 * {@code k8s.namespace} as well is how the sidebar learns whether that facet has any values.
 */
export const REQUESTED_FACET_FIELDS: readonly LogFacetField[] = LOG_FACET_FIELDS

/** The facets the sidebar always renders; {@code k8s.namespace} is only shown when it has data. */
export const ALWAYS_VISIBLE_FACET_FIELDS: readonly LogFacetField[] = ['level', 'host', 'service']

/** Facets whose filter lives in the URL rather than in the filter form. */
const CONTEXT_FACET_FIELDS: readonly LogFacetField[] = ['host', 'service', 'k8s.namespace']

/**
 * The filter the explorer applies for a facet value, or {@code null} when the field has no
 * exact-match filter of its own.
 *
 * <p>{@code level} is a first-class query parameter; the other facet fields are exact matches on
 * the JSON context document.
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
  }
}

export function isFacetValueActive(
  params: LogsQueryParams,
  field: LogFacetField,
  value: string,
): boolean {
  return facetFilterFor(params, field) === value
}

/** True when a context facet filter is active; drives the "Clear" action of the sidebar. */
export function hasActiveFacetFilters(params: LogsQueryParams): boolean {
  return CONTEXT_FACET_FIELDS.some((field) => {
    const value = facetFilterFor(params, field)
    return value !== undefined && value !== null && value !== ''
  })
}

/**
 * Adds the exact-match filter for a facet value, or removes it when the value is already active.
 *
 * <p>The page is reset because the result set changes. The field name never reaches the query
 * string as free text: it selects one of the fixed parameters of the log query contract, and the
 * value is URL-encoded by {@link URLSearchParams}.
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
  return next
}

function isLogLevel(value: string): value is LogLevel {
  return value === 'DEBUG' || value === 'INFO' || value === 'WARN' || value === 'ERROR'
}
