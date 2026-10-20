import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import type { Layout } from 'react-grid-layout'
import type { Widget } from '../../api/dashboards'
import { DashboardGrid, type DashboardGridItem } from '../../components/dashboards/DashboardGrid'
import { DashboardVariablesBar } from '../../components/dashboards/DashboardVariablesBar'
import { DashboardWidget } from '../../components/dashboards/DashboardWidget'
import { DashboardVariablesProvider } from '../../features/dashboards/DashboardVariablesContext'
import { widgetToLayout, resolveGridLayout } from '../../features/dashboards/layout'
import {
  DEFAULT_POLLING_INTERVAL_MS,
  POLLING_OPTIONS,
  sanitizePollingInterval,
} from '../../features/dashboards/polling'
import type { DashboardVariable } from '../../api/dashboards'
import { useSharedDashboardQuery } from '../../api/dashboards'
import { ApiError } from '../../api/client'

export default function PublicDashboardViewPage() {
  const params = useParams<{ token: string }>()
  const token = params.token

  const { data: dashboard, isLoading, isError, error, refetch, isFetching } = useSharedDashboardQuery(token ?? null)

  const [pollInterval, setPollInterval] = useState(DEFAULT_POLLING_INTERVAL_MS)
  const [layout, setLayout] = useState<Layout[] | null>(null)

  const serverLayout = useMemo<Layout[]>(() => (dashboard?.widgets ?? []).map(widgetToLayout), [dashboard])
  const serverSignature = useMemo(() => JSON.stringify(serverLayout), [serverLayout])

  useEffect(() => {
    if (!dashboard) {
      return
    }
    setLayout(serverLayout)
  }, [serverSignature])

  const gridLayout = useMemo(() => resolveGridLayout(layout, serverLayout), [layout, serverLayout])

  const handleLayoutChange = (next: Layout[]) => {
    setLayout(next)
  }

  if (token === undefined) {
    return null // Should not happen with proper routing
  }

  if (isLoading) {
    return <PublicDashboardViewSkeleton />
  }

  // The query returns null for 404 (not found) - check dashboard directly
  if (!dashboard) {
    return (
      <section className="space-y-6">
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6 text-center">
          <h1 className="font-display text-lg font-semibold text-red-200">Dashboard not found or access revoked</h1>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError
              ? error.message
              : error instanceof Error
              ? error.message
              : 'The shared dashboard could not be loaded. The link may be invalid or access has been revoked.'}
          </p>
          <div className="mt-4 flex justify-center gap-2">
            <Button type="button" variant="outline" onClick={() => refetch()} disabled={isFetching}>
              {isFetching ? 'Retrying…' : 'Retry'}
            </Button>
          </div>
        </div>
      </section>
    )
  }

  const variables = dashboard.variables ?? []
  const items: DashboardGridItem[] = dashboard.widgets.map((widget) => ({
    id: widget.id,
    node: (
      <DashboardWidget
        key={widget.id}
        widget={widget}
        showActions={false}
        refetchInterval={pollInterval}
        onEdit={() => {}}
        onDelete={() => {}}
        shareToken={token}
      />
    ),
  }))

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <h1 className="font-display text-2xl font-semibold text-slate-100">{dashboard.name}</h1>
          <p className="text-xs text-slate-500" role="status">
            Public read-only view — changes are not saved
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-2">
            <label htmlFor="dashboard-refresh-public" className="text-xs text-slate-400">
              Refresh
            </label>
            <select
              id="dashboard-refresh-public"
              value={String(pollInterval)}
              onChange={(e) => setPollInterval(sanitizePollingInterval(Number(e.target.value)))}
              className="h-9 w-24 rounded-md border border-white/10 bg-panel/40 px-2 text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary"
            >
              {POLLING_OPTIONS.map((option) => (
                <option key={option.value} value={String(option.value)}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </header>

      <DashboardVariablesProvider variables={variables}>
        {variables.length > 0 ? <DashboardVariablesBar variables={variables} /> : null}

        {dashboard.widgets.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
            <p className="text-sm text-slate-300">This dashboard has no widgets yet.</p>
          </div>
        ) : (
          <DashboardGrid
            items={items}
            layout={gridLayout}
            interactive={false}
            onLayoutChange={handleLayoutChange}
            onLayoutCommit={() => {}}
          />
        )}
      </DashboardVariablesProvider>
    </section>
  )
}

function PublicDashboardViewSkeleton() {
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