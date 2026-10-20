import { useMemo } from 'react'
import { Skeleton } from '../ui/skeleton'
import type { GaugeConfig } from '../../features/dashboards/schemas'
import { useGaugeWidget } from '../../features/dashboards/useWidgetData'
import { metricColor } from '../../features/metrics/catalog'

interface GaugeWidgetProps {
  widgetId: number
  config: GaugeConfig
  refetchInterval: number
  /** Share token of a public dashboard; the data is then read through the public endpoint. */
  shareToken?: string
}

const BYTE_UNITS = ['B', 'KiB', 'MiB', 'GiB', 'TiB']

function formatBytes(bytes: number): string {
  let value = bytes
  let unitIndex = 0
  while (Math.abs(value) >= 1024 && unitIndex < BYTE_UNITS.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${BYTE_UNITS[unitIndex]}`
}

function formatValue(value: number, unit?: string): string {
  if (unit === 'bytes') {
    return formatBytes(value)
  }
  if (unit === 'seconds') {
    return value < 1 ? `${(value * 1000).toFixed(0)} ms` : `${value.toFixed(2)} s`
  }
  if (Number.isInteger(value)) {
    return value.toLocaleString()
  }
  return value.toLocaleString(undefined, { maximumFractionDigits: 2 })
}

export function GaugeWidget({ widgetId, config, refetchInterval, shareToken }: GaugeWidgetProps) {
  const query = useGaugeWidget(widgetId, config, refetchInterval, shareToken)
  const error = query.error instanceof Error ? query.error : null

  const latest = useMemo(() => {
    const points = query.data ?? []
    if (points.length === 0) {
      return null
    }
    return points.reduce((current, point) =>
      Date.parse(point.timestamp) >= Date.parse(current.timestamp) ? point : current,
    )
  }, [query.data])

  const label = config.label ?? config.metricName
  const color = metricColor(config.metricName)

  if (query.isLoading) {
    return <Skeleton className="h-full min-h-24 w-full rounded-xl" />
  }

  if (error) {
    return (
      <div
        role="alert"
        className="flex h-full items-center justify-center rounded-xl border border-red-500/20 bg-red-500/5 p-4"
      >
        <p className="text-center text-sm text-red-300">{error.message || 'Could not load metric data.'}</p>
      </div>
    )
  }

  if (latest === null) {
    return (
      <div className="flex h-full items-center justify-center rounded-xl border border-dashed border-white/10">
        <p className="text-sm text-slate-400">No data for this time range</p>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col items-center justify-center gap-1 rounded-xl border border-white/5 bg-white/[0.02] p-4 text-center">
      <p className="text-xs uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mono text-3xl font-semibold" style={{ color }}>
        {formatValue(latest.value, config.unit)}
      </p>
      {config.unit && config.unit !== 'bytes' && config.unit !== 'seconds' ? (
        <p className="text-xs text-slate-500">{config.unit}</p>
      ) : null}
      <p className="text-[11px] text-slate-500">{new Date(latest.timestamp).toLocaleTimeString()}</p>
    </div>
  )
}
