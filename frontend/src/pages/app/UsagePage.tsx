import { useState } from 'react'
import { Activity, ScrollText, Waypoints, type LucideIcon } from 'lucide-react'
import { ApiError } from '../../api/client'
import type { UsageMeter } from '../../api/usage'
import { useCurrentMonthUsageQuery, useDailyUsageQuery } from '../../api/usage'
import { UsageHistoryChart } from '../../components/usage/UsageHistoryChart'
import { UsageMeterBar } from '../../components/usage/UsageMeterBar'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import { parseUtcInstant } from '../../lib/datetime'

interface MeterConfig {
  key: 'metrics' | 'logs' | 'traces'
  label: string
  icon: LucideIcon
}

const METERS: MeterConfig[] = [
  { key: 'metrics', label: 'Metrics', icon: Activity },
  { key: 'logs', label: 'Logs', icon: ScrollText },
  { key: 'traces', label: 'Traces', icon: Waypoints },
]

function formatPeriod(start: string, end: string): string {
  const from = parseUtcInstant(start)
  const to = parseUtcInstant(end)
  if (!from || !to) {
    return `${start} – ${end}`
  }
  const sameYear = from.getUTCFullYear() === to.getUTCFullYear()
  const fromLabel = from.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: sameYear ? undefined : 'numeric',
    timeZone: 'UTC',
  })
  const toLabel = to.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC',
  })
  return `${fromLabel} – ${toLabel}`
}

export default function UsagePage() {
  const [days, setDays] = useState(30)
  const currentMonth = useCurrentMonthUsageQuery()
  const daily = useDailyUsageQuery(days)

  const meters: Record<MeterConfig['key'], UsageMeter> | null = currentMonth.data
    ? {
        metrics: currentMonth.data.metrics,
        logs: currentMonth.data.logs,
        traces: currentMonth.data.traces,
      }
    : null

  return (
    <section className="space-y-8">
      <header className="space-y-2">
        <p className="section-tag">Usage</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">Usage &amp; quotas</h1>
        <p className="text-sm text-slate-400">
          Telemetry ingested by this organization in the current billing period.
          {currentMonth.data
            ? ` Period: ${formatPeriod(currentMonth.data.periodStart, currentMonth.data.periodEnd)}.`
            : ''}
        </p>
      </header>

      {currentMonth.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true">
          {METERS.map((meter) => (
            <Skeleton key={meter.key} className="h-40 w-full rounded-2xl" />
          ))}
        </div>
      ) : currentMonth.isError || !meters ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h3 className="font-display text-base font-semibold text-red-200">Could not load usage</h3>
          <p className="mt-2 text-sm text-slate-400">
            {currentMonth.error instanceof ApiError
              ? currentMonth.error.message
              : 'Something went wrong while loading usage.'}
          </p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={() => void currentMonth.refetch()}
            disabled={currentMonth.isFetching}
          >
            {currentMonth.isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {METERS.map((meter) => {
            const value = meters[meter.key]
            return (
              <UsageMeterBar
                key={meter.key}
                label={meter.label}
                icon={meter.icon}
                used={value.used}
                limit={value.limit}
                percentage={value.percentage}
              />
            )
          })}
        </div>
      )}

      <UsageHistoryChart
        data={daily.data ?? []}
        days={days}
        onDaysChange={setDays}
        isLoading={daily.isLoading}
        error={daily.isError ? (daily.error as Error) : null}
      />
    </section>
  )
}
