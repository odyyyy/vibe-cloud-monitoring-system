import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { DailyUsagePoint } from '../../api/usage'
import { Button } from '../ui/button'
import { Skeleton } from '../ui/skeleton'

interface UsageHistoryChartProps {
  data: DailyUsagePoint[]
  days: number
  onDaysChange: (days: number) => void
  isLoading: boolean
  error?: Error | null
}

const WINDOW_OPTIONS = [7, 30, 90] as const

const SERIES = [
  { key: 'metrics', label: 'Metrics', color: '#22d3ee' },
  { key: 'logs', label: 'Logs', color: '#a78bfa' },
  { key: 'traces', label: 'Traces', color: '#facc15' },
] as const

function shortDate(value: string): string {
  const parsed = new Date(`${value}T00:00:00Z`)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return parsed.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
}

export function UsageHistoryChart({ data, days, onDaysChange, isLoading, error }: UsageHistoryChartProps) {
  return (
    <section className="rounded-2xl border border-white/10 bg-panel/60 p-4 sm:p-6">
      <header className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-display text-base font-semibold text-slate-100">Daily usage</h2>
          <p className="text-xs text-slate-500">Telemetry items per day (UTC)</p>
        </div>
        <div className="flex items-center gap-1 rounded-lg border border-white/10 bg-panel/80 p-0.5">
          {WINDOW_OPTIONS.map((option) => (
            <Button
              key={option}
              type="button"
              size="sm"
              variant={option === days ? 'secondary' : 'ghost'}
              aria-pressed={option === days}
              onClick={() => onDaysChange(option)}
            >
              {option}d
            </Button>
          ))}
        </div>
      </header>

      {isLoading ? (
        <Skeleton className="h-72 w-full rounded-xl" />
      ) : error ? (
        <div
          role="alert"
          className="flex h-72 items-center justify-center rounded-xl border border-red-500/20 bg-red-500/5"
        >
          <p className="px-4 text-center text-sm text-red-300">
            {error.message || 'Could not load usage history.'}
          </p>
        </div>
      ) : data.length === 0 ? (
        <div className="flex h-72 items-center justify-center rounded-xl border border-dashed border-white/10">
          <p className="text-sm text-slate-400">No usage recorded for this period</p>
        </div>
      ) : (
        <div className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.12)" vertical={false} />
              <XAxis
                dataKey="date"
                tickFormatter={shortDate}
                stroke="#64748b"
                fontSize={11}
                minTickGap={24}
              />
              <YAxis stroke="#64748b" fontSize={11} width={56} allowDecimals={false} />
              <Tooltip
                labelFormatter={(value) => shortDate(String(value))}
                contentStyle={{
                  background: '#0f172a',
                  border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: 8,
                  fontSize: 12,
                }}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              {SERIES.map((series) => (
                <Bar
                  key={series.key}
                  dataKey={series.key}
                  name={series.label}
                  fill={series.color}
                  isAnimationActive={false}
                />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  )
}
