import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import { useMetricsQuery } from '../../api/metrics'
import { MetricChart } from '../../components/metrics/MetricChart'
import { MetricsFilterPanel } from '../../components/metrics/MetricsFilterPanel'
import { metricsQuerySchema } from '../../features/metrics/schemas'
import {
  DEFAULT_METRIC_FILTERS,
  filtersFromSearchParams,
  filtersToSearchParams,
  parseMetricSearchParams,
  type MetricFilters,
} from '../../features/metrics/searchParams'
import { TIME_RANGE_PRESETS, resolvePresetRange } from '../../features/metrics/timeRange'

export default function MetricsExplorerPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchKey = searchParams.toString()

  const initialFilters = useMemo<MetricFilters>(
    () => ({
      ...DEFAULT_METRIC_FILTERS,
      range: resolvePresetRange(TIME_RANGE_PRESETS[0]),
    }),
    [],
  )

  const [draft, setDraft] = useState<MetricFilters>(() =>
    filtersFromSearchParams(searchParams, initialFilters),
  )
  const [applyError, setApplyError] = useState<string | null>(null)

  useEffect(() => {
    setDraft(filtersFromSearchParams(new URLSearchParams(searchKey), initialFilters))
  }, [searchKey, initialFilters])

  const parsed = useMemo(() => parseMetricSearchParams(new URLSearchParams(searchKey)), [searchKey])
  const params = parsed.params

  const query = useMetricsQuery(params, params !== null)

  useEffect(() => {
    if (query.error instanceof ApiError && (query.error.status === 400 || query.error.status === 403)) {
      toast.error(query.error.message)
    }
  }, [query.error])

  const handleChange = (patch: Partial<MetricFilters>) => {
    setDraft((current) => ({ ...current, ...patch }))
  }

  const handleApply = () => {
    const result = metricsQuerySchema.safeParse({
      applicationId: draft.applicationId ?? undefined,
      metricName: draft.metricName,
      from: draft.range?.from ?? undefined,
      to: draft.range?.to ?? undefined,
      aggregation: draft.aggregation,
      intervalMinutes: draft.intervalMinutes,
    })

    if (!result.success) {
      setApplyError(result.error.issues[0]?.message ?? 'Invalid filters')
      return
    }

    setApplyError(null)
    setSearchParams(filtersToSearchParams(result.data))
  }

  const validationError = parsed.error ?? applyError

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="section-tag">Metrics</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">Metrics explorer</h1>
        <p className="text-sm text-slate-400">
          Query aggregated time series and visualize how a metric changes over time.
        </p>
      </header>

      <MetricsFilterPanel
        draft={draft}
        onChange={handleChange}
        onApply={handleApply}
        error={validationError}
        isApplying={query.isFetching}
      />

      {params ? (
        <MetricChart
          title={`${params.metricName} · ${params.aggregation}`}
          data={query.data ?? []}
          isLoading={query.isLoading}
          error={query.error instanceof ApiError ? query.error : null}
        />
      ) : (
        <div className="flex h-72 items-center justify-center rounded-2xl border border-dashed border-white/10 bg-[#0b101b]/40">
          <p className="text-sm text-slate-400">
            {validationError ?? 'Choose an application, enter a metric name and apply filters to load data.'}
          </p>
        </div>
      )}
    </section>
  )
}
