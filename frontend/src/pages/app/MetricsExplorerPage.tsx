import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ApiError, type ExportFormat } from '../../api/client'
import { exportMetrics, useMetricsQuery } from '../../api/metrics'
import { MetricChart } from '../../components/metrics/MetricChart'
import { MetricsFilterPanel } from '../../components/metrics/MetricsFilterPanel'
import { metricsQuerySchema } from '../../features/metrics/schemas'
import { metricColor, metricLabel } from '../../features/metrics/catalog'
import {
  DEFAULT_METRIC_FILTERS,
  filtersFromSearchParams,
  filtersToSearchParams,
  parseMetricSearchParams,
  type MetricFilters,
} from '../../features/metrics/searchParams'
import { TIME_RANGE_PRESETS, resolvePresetRange } from '../../features/metrics/timeRange'
import { downloadBlob } from '../../lib/download'

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
  const [isExporting, setIsExporting] = useState(false)

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

  const handleExport = async (format: ExportFormat) => {
    if (!params) {
      toast.error('Apply filters before exporting.')
      return
    }

    setIsExporting(true)
    try {
      const file = await exportMetrics(params, format)
      downloadBlob(file.blob, file.filename ?? `metrics-export.${format}`)
      if (file.truncated) {
        toast.warning('Export limited to first 10,000 rows. Refine your filters.')
      }
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not export metrics.')
    } finally {
      setIsExporting(false)
    }
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
        onExport={handleExport}
        error={validationError}
        isApplying={query.isFetching}
        isExporting={isExporting}
        canExport={params !== null}
      />

      {params ? (
        <MetricChart
          title={`${metricLabel(params.metricName)} · ${params.aggregation}`}
          accentColor={metricColor(params.metricName)}
          data={query.data ?? []}
          isLoading={query.isLoading}
          error={query.error instanceof ApiError ? query.error : null}
        />
      ) : (
        <div className="flex h-72 items-center justify-center rounded-2xl border border-dashed border-white/10 bg-panel/40">
          <p className="text-sm text-slate-400">
            {validationError ?? 'Choose an application, enter a metric name and apply filters to load data.'}
          </p>
        </div>
      )}
    </section>
  )
}
