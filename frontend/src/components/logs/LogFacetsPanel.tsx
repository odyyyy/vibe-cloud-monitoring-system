import { useMemo } from 'react'
import { ChevronLeft, ChevronRight, SlidersHorizontal } from 'lucide-react'
import { ApiError } from '../../api/client'
import type { LogFacetField, LogFacetValue, LogsQueryParams } from '../../api/logs'
import { useLogFacetsQuery } from '../../api/logs'
import {
  ALWAYS_VISIBLE_FACET_FIELDS,
  FACET_FIELD_LABELS,
  REQUESTED_FACET_FIELDS,
  hasActiveFacetFilters,
  isFacetValueActive,
} from '../../features/logs/facets'
import { cn } from '../../lib/utils'
import { Button } from '../ui/button'
import { Skeleton } from '../ui/skeleton'

interface LogFacetsPanelProps {
  /** The committed log query; the facets describe exactly this row set. */
  params: LogsQueryParams
  collapsed: boolean
  onCollapsedChange: (collapsed: boolean) => void
  onToggleValue: (field: LogFacetField, value: string) => void
  onClearFilters: () => void
}

/**
 * Top-N sidebar for the log explorer.
 *
 * <p>The panel is deliberately independent of the result list: it loads in parallel, never
 * blocks the table, and shows a compact error of its own when the aggregation fails or times out.
 */
export function LogFacetsPanel({
  params,
  collapsed,
  onCollapsedChange,
  onToggleValue,
  onClearFilters,
}: LogFacetsPanelProps) {
  const facetsQuery = useLogFacetsQuery(params, REQUESTED_FACET_FIELDS, !collapsed)
  const facets = facetsQuery.data?.facets
  const hasFilters = hasActiveFacetFilters(params)

  const visibleFields = useMemo(
    () =>
      REQUESTED_FACET_FIELDS.filter((field) => {
        if (ALWAYS_VISIBLE_FACET_FIELDS.includes(field)) {
          return true
        }
        // Optional facets are only shown when the current row set actually has values for them.
        return (facets?.[field] ?? []).length > 0
      }),
    [facets],
  )

  if (collapsed) {
    return (
      <aside className="rounded-2xl border border-white/10 bg-panel/60 p-2">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="w-full justify-start gap-2"
          onClick={() => onCollapsedChange(false)}
          aria-expanded={false}
        >
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
          <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
          Facets
        </Button>
      </aside>
    )
  }

  return (
    <aside className="rounded-2xl border border-white/10 bg-panel/60 p-4" aria-label="Facets">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-display text-sm font-semibold text-slate-200">Facets</h2>
        <div className="flex items-center gap-1">
          {hasFilters ? (
            <Button type="button" variant="ghost" size="sm" className="h-8 px-2 text-xs" onClick={onClearFilters}>
              Clear
            </Button>
          ) : null}
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            onClick={() => onCollapsedChange(true)}
            aria-expanded
            aria-label="Collapse facets"
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>

      {facetsQuery.isError ? (
        <FacetError error={facetsQuery.error} onRetry={() => facetsQuery.refetch()} isRetrying={facetsQuery.isFetching} />
      ) : (
        <>
          {facetsQuery.isLoading ? (
            <FacetSkeleton />
          ) : (
            <div className="mt-3 space-y-5">
              {visibleFields.map((field) => (
                <FacetGroup
                  key={field}
                  field={field}
                  values={facets?.[field] ?? []}
                  params={params}
                  onToggleValue={onToggleValue}
                />
              ))}
            </div>
          )}

          {facetsQuery.data?.approximate ? (
            <p className="mt-4 text-xs text-slate-500">
              Based on the most recent {facetsQuery.data.scanLimit.toLocaleString()} matching logs.
            </p>
          ) : null}
        </>
      )}
    </aside>
  )
}

interface FacetGroupProps {
  field: LogFacetField
  values: LogFacetValue[]
  params: LogsQueryParams
  onToggleValue: (field: LogFacetField, value: string) => void
}

function FacetGroup({ field, values, params, onToggleValue }: FacetGroupProps) {
  const maxCount = values.reduce((max, value) => Math.max(max, value.count), 0)

  return (
    <section aria-labelledby={`facet-${field}`}>
      <h3 id={`facet-${field}`} className="text-xs font-semibold uppercase tracking-wide text-slate-400">
        {FACET_FIELD_LABELS[field]}
      </h3>

      {values.length === 0 ? (
        <p className="mt-1.5 text-xs text-slate-500">No values</p>
      ) : (
        <ul className="mt-1.5 space-y-1">
          {values.map((value) => {
            const active = isFacetValueActive(params, field, value.value)
            const width = maxCount > 0 ? Math.max(4, Math.round((value.count / maxCount) * 100)) : 0
            return (
              <li key={value.value}>
                <button
                  type="button"
                  onClick={() => onToggleValue(field, value.value)}
                  aria-pressed={active}
                  title={active ? `Remove the ${FACET_FIELD_LABELS[field]} filter` : `Filter by ${value.value}`}
                  className={cn(
                    'group relative w-full overflow-hidden rounded-md px-2 py-1 text-left transition-colors hover:bg-white/5',
                    active ? 'bg-cyan-400/10 ring-1 ring-inset ring-cyan-400/30' : undefined,
                  )}
                >
                  <span
                    aria-hidden="true"
                    className={cn(
                      'absolute inset-y-0 left-0 rounded-r-sm',
                      active ? 'bg-cyan-400/20' : 'bg-white/5',
                    )}
                    style={{ width: `${width}%` }}
                  />
                  <span className="relative flex items-center justify-between gap-2 text-xs">
                    <span className={cn('truncate', active ? 'text-cyan-100' : 'text-slate-300')}>
                      {value.value}
                    </span>
                    <span className="shrink-0 font-mono text-[11px] text-slate-400">
                      {value.count.toLocaleString()}
                    </span>
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

function FacetSkeleton() {
  return (
    <div className="mt-3 space-y-5" aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading facets</span>
      {[0, 1, 2].map((group) => (
        <div key={group} className="space-y-1.5">
          <Skeleton className="h-3 w-16" />
          <Skeleton className="h-6 w-full" />
          <Skeleton className="h-6 w-5/6" />
          <Skeleton className="h-6 w-2/3" />
        </div>
      ))}
    </div>
  )
}

interface FacetErrorProps {
  error: unknown
  onRetry: () => void
  isRetrying: boolean
}

/** Non-blocking error state: the log table keeps working without facets. */
function FacetError({ error, onRetry, isRetrying }: FacetErrorProps) {
  const message =
    error instanceof ApiError && error.status === 429
      ? 'Too many facet requests. Try again in a moment.'
      : error instanceof ApiError && error.status === 503
        ? 'Facet aggregation timed out. Narrow the time range.'
        : 'Facets unavailable.'

  return (
    <div className="mt-3 space-y-2" role="status">
      <p className="text-xs text-amber-200">{message}</p>
      <Button type="button" size="sm" variant="outline" onClick={onRetry} disabled={isRetrying}>
        {isRetrying ? 'Retrying...' : 'Retry'}
      </Button>
    </div>
  )
}
