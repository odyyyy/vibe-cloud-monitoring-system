import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { AggregatedMetricPoint } from '../../api/metrics'
import { cn } from '../../lib/utils'
import { Skeleton } from '../ui/skeleton'

interface MetricChartProps {
  data: AggregatedMetricPoint[]
  title: string
  isLoading: boolean
  error?: Error | null
  /** Renders just the chart area without the surrounding panel/header (for widgets). */
  bare?: boolean
  heightClass?: string
}

interface ChartPoint {
  timestamp: number
  value: number
}

interface ChartTooltipProps {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: ChartPoint }>
}

function ChartTooltip({ active, payload }: ChartTooltipProps) {
  const point = active ? payload?.[0]?.payload : undefined
  if (!point) {
    return null
  }
  return (
    <div className="rounded-lg border border-white/10 bg-[#0b101b] px-3 py-2 text-xs shadow-xl">
      <p className="text-slate-400">{new Date(point.timestamp).toLocaleString()}</p>
      <p className="mono mt-1 text-cyan-200">{point.value}</p>
    </div>
  )
}

function toChartData(data: AggregatedMetricPoint[]): ChartPoint[] {
  return data
    .map((point) => ({ timestamp: Date.parse(point.timestamp), value: point.value }))
    .filter((point) => !Number.isNaN(point.timestamp))
    .sort((a, b) => a.timestamp - b.timestamp)
}

export function MetricChart({ data, title, isLoading, error, bare = false, heightClass }: MetricChartProps) {
  const chartData = toChartData(data)
  const height = heightClass ?? 'h-72'

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
    <div className={cn(height, 'w-full')}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.12)" />
          <XAxis
            dataKey="timestamp"
            type="number"
            domain={['dataMin', 'dataMax']}
            scale="time"
            tickFormatter={(value: number) => new Date(value).toLocaleTimeString()}
            stroke="#64748b"
            fontSize={11}
            minTickGap={32}
          />
          <YAxis stroke="#64748b" fontSize={11} width={56} />
          <Tooltip content={<ChartTooltip />} />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#22d3ee"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
            connectNulls={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )

  if (bare) {
    return <div className="flex h-full min-h-0 flex-col">{body}</div>
  }

  return (
    <section className="rounded-2xl border border-white/10 bg-[#0b101b]/60 p-4 sm:p-6">
      <header className="mb-4 flex items-center justify-between">
        <h2 className="font-display text-base font-semibold text-slate-100">{title}</h2>
      </header>

      {body}
    </section>
  )
}
