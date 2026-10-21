import { useMemo, useState } from 'react'
import { Lock, ShieldCheck } from 'lucide-react'
import { ApiError } from '../../api/client'
import { usePlatformHealthQuery } from '../../api/platformHealth'
import { DashboardGrid, type DashboardGridItem } from '../../components/dashboards/DashboardGrid'
import { DashboardWidget } from '../../components/dashboards/DashboardWidget'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { Skeleton } from '../../components/ui/skeleton'
import { DashboardVariablesProvider } from '../../features/dashboards/DashboardVariablesContext'
import { widgetToLayout } from '../../features/dashboards/layout'
import {
  DEFAULT_POLLING_INTERVAL_MS,
  POLLING_OPTIONS,
  sanitizePollingInterval,
} from '../../features/dashboards/polling'

/**
 * Read-only view of the platform self-monitoring dashboard.
 *
 * <p>The dashboard belongs to the hidden System organization and is served by the
 * platform-administration API, which requires the PLATFORM_ADMIN role. Layout, widgets and
 * sharing are not editable here.
 */
export default function PlatformHealthPage() {
  const { data: dashboard, isLoading, isError, error, refetch, isFetching } = usePlatformHealthQuery()
  const [pollInterval, setPollInterval] = useState(DEFAULT_POLLING_INTERVAL_MS)

  const layout = useMemo(() => (dashboard?.widgets ?? []).map(widgetToLayout), [dashboard])

  if (isLoading) {
    return <PlatformHealthSkeleton />
  }

  if (isError || !dashboard) {
    return (
      <section>
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h1 className="font-display text-lg font-semibold text-red-200">Could not load platform health</h1>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError
              ? error.message
              : 'The platform health dashboard could not be loaded. Platform administrator access is required.'}
          </p>
          <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      </section>
    )
  }

  const variables = dashboard.variables ?? []
  const items: DashboardGridItem[] = dashboard.widgets.map((widget) => ({
    id: widget.id,
    node: (
      <DashboardWidget
        widget={widget}
        showActions={false}
        refetchInterval={pollInterval}
        onEdit={noop}
        onDelete={noop}
      />
    ),
  }))

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Platform administration</p>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="font-display text-2xl font-semibold text-slate-100">{dashboard.name}</h1>
            <Badge variant="info" className="inline-flex items-center gap-1">
              <ShieldCheck className="h-3 w-3" aria-hidden="true" />
              Platform admin
            </Badge>
            <Badge variant="muted" className="inline-flex items-center gap-1">
              <Lock className="h-3 w-3" aria-hidden="true" />
              Read-only
            </Badge>
          </div>
          <p className="text-sm text-slate-400">
            The platform observes itself: JVM, database pool and ingestion telemetry of this deployment.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Label htmlFor="platform-health-refresh" className="text-xs text-slate-400">
            Refresh
          </Label>
          <Select
            value={String(pollInterval)}
            onValueChange={(value) => setPollInterval(sanitizePollingInterval(Number(value)))}
          >
            <SelectTrigger id="platform-health-refresh" className="h-9 w-24">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {POLLING_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={String(option.value)}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </header>

      <DashboardVariablesProvider variables={variables}>
        {dashboard.widgets.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
            <p className="text-sm text-slate-300">This dashboard has no widgets yet.</p>
          </div>
        ) : (
          <DashboardGrid items={items} layout={layout} interactive={false} onLayoutChange={noop} />
        )}
      </DashboardVariablesProvider>
    </section>
  )
}

function noop() {
  return undefined
}

function PlatformHealthSkeleton() {
  return (
    <section className="space-y-6" aria-busy="true">
      <Skeleton className="h-8 w-56" />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {[0, 1].map((item) => (
          <Skeleton key={item} className="h-72 w-full rounded-2xl" />
        ))}
      </div>
    </section>
  )
}
