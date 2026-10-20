import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import { Maximize2, ZoomIn, ZoomOut } from 'lucide-react'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { AggregatedMetricPoint } from '../../api/metrics'
import { cn } from '../../lib/utils'
import { Button } from '../ui/button'
import { Skeleton } from '../ui/skeleton'
import {
  clampDomain,
  domainsEqual,
  panDomain,
  pixelToRatio,
  zoomDomain,
  type Domain,
} from '../../features/metrics/chartZoom'

interface MetricChartProps {
  data: AggregatedMetricPoint[]
  title: string
  isLoading: boolean
  error?: Error | null
  /** Renders just the chart area without the surrounding panel/header (for widgets). */
  bare?: boolean
  heightClass?: string
  /** Line color; callers pass the accent color of the metric being drawn. */
  accentColor?: string
}

interface ChartRow {
  timestamp: number
  [seriesKey: string]: number | undefined
}

interface ChartSeries {
  key: string
  name: string
  color: string
}

interface ChartTooltipProps {
  active?: boolean
  label?: number | string
  payload?: ReadonlyArray<{
    dataKey?: string | number
    name?: string
    value?: number | string
    color?: string
    payload?: ChartRow
  }>
  accentColor?: string
}

const DEFAULT_ACCENT_COLOR = '#22d3ee'
const SERIES_COLORS = ['#22d3ee', '#f59e0b', '#34d399', '#f472b6', '#a78bfa', '#f87171', '#60a5fa', '#facc15']

const Y_AXIS_WIDTH = 56
const CHART_MARGIN = { top: 8, right: 16, bottom: 8, left: 0 }
const ZOOM_STEP = 0.6
const PAN_CURSOR_CLASS = 'cursor-grab'
const PANNING_CURSOR_CLASS = 'cursor-grabbing'

function ChartTooltip({ active, label, payload, accentColor }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) {
    return null
  }
  const timestamp = typeof label === 'number' ? label : Number(label)
  return (
    <div className="rounded-lg border border-white/10 bg-panel px-3 py-2 text-xs shadow-xl">
      <p className="text-slate-400">
        {Number.isNaN(timestamp) ? '' : new Date(timestamp).toLocaleString()}
      </p>
      <div className="mt-1 space-y-0.5">
        {payload.map((item) => (
          <div
            key={String(item.dataKey)}
            className="flex items-center justify-between gap-4"
            style={{ color: item.color ?? accentColor ?? DEFAULT_ACCENT_COLOR }}
          >
            <span className="text-slate-400">{item.name ?? 'Value'}</span>
            <span className="mono">{item.value}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * Converts the flat response into chart rows and series. A response with `group` values is
 * rendered as one line per tag value (for example ingestion status codes); an ungrouped
 * response stays a single line.
 */
function toChartData(
  data: AggregatedMetricPoint[],
  accentColor: string,
  title: string,
): { rows: ChartRow[]; series: ChartSeries[] } {
  const grouped = data.some((point) => point.group !== undefined && point.group !== null)

  if (!grouped) {
    const rows = data
      .map((point) => ({ timestamp: Date.parse(point.timestamp), value: point.value }))
      .filter((row) => !Number.isNaN(row.timestamp))
      .sort((a, b) => a.timestamp - b.timestamp)
    return {
      rows,
      series: [{ key: 'value', name: title, color: accentColor }],
    }
  }

  const groupNames = Array.from(
    new Set(data.map((point) => (point.group === null || point.group === undefined ? 'unknown' : point.group))),
  ).sort()
  const keyByGroup = new Map(groupNames.map((group, index) => [group, `series_${index}`]))

  const rowsByTimestamp = new Map<number, ChartRow>()
  for (const point of data) {
    const timestamp = Date.parse(point.timestamp)
    if (Number.isNaN(timestamp)) {
      continue
    }
    const group = point.group === null || point.group === undefined ? 'unknown' : point.group
    const key = keyByGroup.get(group) as string
    const row = rowsByTimestamp.get(timestamp) ?? { timestamp }
    row[key] = point.value
    rowsByTimestamp.set(timestamp, row)
  }

  return {
    rows: Array.from(rowsByTimestamp.values()).sort((a, b) => a.timestamp - b.timestamp),
    series: groupNames.map((group, index) => ({
      key: keyByGroup.get(group) as string,
      name: group,
      color: SERIES_COLORS[index % SERIES_COLORS.length],
    })),
  }
}

export function MetricChart({
  data,
  title,
  isLoading,
  error,
  bare = false,
  heightClass,
  accentColor,
}: MetricChartProps) {
  const height = heightClass ?? 'h-72'
  const accent = accentColor ?? DEFAULT_ACCENT_COLOR
  const { rows: chartData, series } = useMemo(
    () => toChartData(data, accent, title),
    [data, accent, title],
  )

  const bounds = useMemo<Domain | null>(() => {
    if (chartData.length < 2) {
      return null
    }
    return [chartData[0].timestamp, chartData[chartData.length - 1].timestamp]
  }, [chartData])

  const [domain, setDomain] = useState<Domain | null>(null)
  const [isPanning, setIsPanning] = useState(false)
  const chartAreaRef = useRef<HTMLDivElement>(null)
  const panStartRef = useRef<{ x: number; domain: Domain } | null>(null)

  const lo = bounds ? bounds[0] : null
  const hi = bounds ? bounds[1] : null

  useEffect(() => {
    if (lo === null || hi === null) {
      setDomain(null)
      return
    }
    setDomain((current) => {
      if (!current) {
        return current
      }
      if (current[1] < lo || current[0] > hi) {
        return null
      }
      const next = clampDomain(current, [lo, hi])
      return domainsEqual(next, current) ? current : next
    })
  }, [lo, hi])

  const plotGeometry = useCallback((): { left: number; width: number } | null => {
    const element = chartAreaRef.current
    if (!element) {
      return null
    }
    const rect = element.getBoundingClientRect()
    return {
      left: rect.left + CHART_MARGIN.left + Y_AXIS_WIDTH,
      width: Math.max(rect.width - CHART_MARGIN.left - CHART_MARGIN.right - Y_AXIS_WIDTH, 1),
    }
  }, [])

  const applyDomain = useCallback(
    (next: Domain) => {
      if (!bounds) {
        return
      }
      setDomain(domainsEqual(next, bounds) ? null : next)
    },
    [bounds],
  )

  const zoomAt = useCallback(
    (factor: number, anchorRatio: number) => {
      if (!bounds) {
        return
      }
      applyDomain(zoomDomain(domain ?? bounds, bounds, factor, anchorRatio))
    },
    [applyDomain, bounds, domain],
  )

  useEffect(() => {
    const element = chartAreaRef.current
    if (!element || !bounds) {
      return
    }

    const handleWheel = (event: WheelEvent) => {
      const geometry = plotGeometry()
      if (!geometry) {
        return
      }
      event.preventDefault()
      const ratio = pixelToRatio(event.clientX, geometry.left, geometry.width)
      zoomAt(event.deltaY > 0 ? 1 / ZOOM_STEP : ZOOM_STEP, ratio)
    }

    element.addEventListener('wheel', handleWheel, { passive: false })
    return () => element.removeEventListener('wheel', handleWheel)
  }, [bounds, plotGeometry, zoomAt])

  const handleMouseDown = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (!bounds || event.button !== 0) {
      return
    }
    event.preventDefault()
    panStartRef.current = { x: event.clientX, domain: domain ?? bounds }
    setIsPanning(true)
  }

  useEffect(() => {
    if (!isPanning) {
      return
    }

    const handleMove = (event: MouseEvent) => {
      const start = panStartRef.current
      const geometry = plotGeometry()
      if (!start || !geometry || !bounds) {
        return
      }
      const deltaRatio = (start.x - event.clientX) / geometry.width
      applyDomain(panDomain(start.domain, bounds, deltaRatio))
    }

    const handleUp = () => {
      panStartRef.current = null
      setIsPanning(false)
    }

    window.addEventListener('mousemove', handleMove)
    window.addEventListener('mouseup', handleUp)
    return () => {
      window.removeEventListener('mousemove', handleMove)
      window.removeEventListener('mouseup', handleUp)
    }
  }, [applyDomain, bounds, isPanning, plotGeometry])

  const canInteract = bounds !== null
  const isZoomed = domain !== null
  const xAxisDomain: [number | 'dataMin' | 'dataMax', number | 'dataMin' | 'dataMax'] = domain
    ? [domain[0], domain[1]]
    : ['dataMin', 'dataMax']

  const body = isLoading ? (
    <Skeleton className={cn(height, 'w-full rounded-xl')} />
  ) : error ? (
    <div role="alert" className={cn(height, 'flex items-center justify-center rounded-xl border border-red-500/20 bg-red-500/5')}>
      <p className="px-4 text-center text-sm text-red-300">{error.message || 'Could not load metric data.'}</p>
    </div>
  ) : chartData.length === 0 ? (
    <div className={cn(height, 'flex items-center justify-center rounded-xl border border-dashed border-white/10')}>
      <p className="text-sm text-slate-400">No data for this time range</p>
    </div>
  ) : (
    <div
      ref={chartAreaRef}
      className={cn(
        height,
        'relative w-full select-none',
        canInteract && (isPanning ? PANNING_CURSOR_CLASS : PAN_CURSOR_CLASS),
      )}
      onMouseDown={canInteract ? handleMouseDown : undefined}
      onDoubleClick={isZoomed ? () => setDomain(null) : undefined}
    >
      {canInteract ? (
        <div
          className="absolute right-1 top-1 z-10 flex items-center gap-0.5 rounded-lg border border-white/10 bg-panel/80 p-0.5 backdrop-blur"
          onMouseDown={(event) => event.stopPropagation()}
          onDoubleClick={(event) => event.stopPropagation()}
        >
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-6 w-6"
            aria-label="Zoom in"
            title="Zoom in"
            onClick={() => zoomAt(ZOOM_STEP, 0.5)}
          >
            <ZoomIn className="h-3.5 w-3.5" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-6 w-6"
            aria-label="Zoom out"
            title="Zoom out"
            onClick={() => zoomAt(1 / ZOOM_STEP, 0.5)}
          >
            <ZoomOut className="h-3.5 w-3.5" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-6 w-6"
            aria-label="Reset zoom"
            title="Reset zoom"
            disabled={!isZoomed}
            onClick={() => setDomain(null)}
          >
            <Maximize2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      ) : null}
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData} margin={CHART_MARGIN}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.12)" />
          <XAxis
            dataKey="timestamp"
            type="number"
            domain={xAxisDomain}
            allowDataOverflow
            scale="time"
            tickFormatter={(value: number) => new Date(value).toLocaleTimeString()}
            stroke="#64748b"
            fontSize={11}
            minTickGap={32}
          />
          <YAxis stroke="#64748b" fontSize={11} width={Y_AXIS_WIDTH} />
          <Tooltip content={<ChartTooltip accentColor={accent} />} />
          {series.length > 1 ? (
            <Legend wrapperStyle={{ fontSize: 11, color: '#94a3b8' }} />
          ) : null}
          {series.map((item) => (
            <Line
              key={item.key}
              type="monotone"
              dataKey={item.key}
              name={item.name}
              stroke={item.color}
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
              connectNulls={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )

  if (bare) {
    return <div className="flex h-full min-h-0 flex-col">{body}</div>
  }

  return (
    <section className="rounded-2xl border border-white/10 bg-panel/60 p-4 sm:p-6">
      <header className="mb-4 flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-display text-base font-semibold text-slate-100">
          <span
            aria-hidden="true"
            className="h-2.5 w-2.5 shrink-0 rounded-full"
            style={{ backgroundColor: accent }}
          />
          {title}
        </h2>
        {canInteract && !error && !isLoading ? (
          <p className="hidden text-xs text-slate-500 sm:block">Scroll to zoom · drag to pan · double-click to reset</p>
        ) : null}
      </header>

      {body}
    </section>
  )
}
