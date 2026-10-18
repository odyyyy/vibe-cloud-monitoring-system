import { useMemo, useState, type DragEvent } from 'react'
import { Link } from 'react-router-dom'
import { LayoutGrid } from 'lucide-react'
import { ApiError } from '../../api/client'
import type { Dashboard } from '../../api/dashboards'
import { useDashboardsQuery } from '../../api/dashboards'
import { DashboardSection } from '../../components/dashboards/DashboardSection'
import { WorkspaceSummaryCards } from '../../components/overview/WorkspaceSummaryCards'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import { DEFAULT_POLLING_INTERVAL_MS } from '../../features/dashboards/polling'
import { useDashboardOrder } from '../../features/dashboards/useDashboardOrder'
import { useAuthStore } from '../../store/authStore'

export default function OverviewPage() {
  const user = useAuthStore((state) => state.user)
  const { data, isLoading, isError, error, isFetching, refetch } = useDashboardsQuery()
  const dashboards = useMemo(() => data ?? [], [data])
  const dashboardIds = useMemo(() => dashboards.map((dashboard) => dashboard.id), [dashboards])

  const { orderedIds, moveBy, moveToIndex } = useDashboardOrder(user?.id ?? null, dashboardIds)

  const orderedDashboards = useMemo(() => {
    const byId = new Map(dashboards.map((dashboard) => [dashboard.id, dashboard]))
    return orderedIds
      .map((id) => byId.get(id))
      .filter((dashboard): dashboard is Dashboard => dashboard !== undefined)
  }, [orderedIds, dashboards])

  const [draggingId, setDraggingId] = useState<number | null>(null)

  const handleDragOver = (event: DragEvent<HTMLElement>) => {
    if (draggingId !== null) {
      event.preventDefault()
    }
  }

  const handleDrop = (targetIndex: number) => {
    if (draggingId !== null) {
      moveToIndex(draggingId, targetIndex)
      setDraggingId(null)
    }
  }

  return (
    <section className="space-y-8">
      <header className="space-y-2">
        <p className="section-tag">Overview</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">
          Welcome back{user?.username ? `, ${user.username}` : ''}
        </h1>
        <p className="text-sm text-slate-400">
          Your workspace is ready. Telemetry views will appear here as you connect agents and start
          sending data.
        </p>
      </header>

      <WorkspaceSummaryCards />

      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <LayoutGrid className="h-4 w-4 text-slate-400" aria-hidden="true" />
            <h2 className="font-display text-lg font-semibold text-slate-100">Dashboards</h2>
          </div>
          <Button asChild variant="ghost" size="sm">
            <Link to="/app/dashboards">Manage dashboards</Link>
          </Button>
        </div>

        {isLoading ? (
          <div aria-busy="true" className="space-y-4">
            {[0, 1].map((item) => (
              <Skeleton key={item} className="h-64 w-full rounded-2xl" />
            ))}
          </div>
        ) : isError ? (
          <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
            <h3 className="font-display text-base font-semibold text-red-200">Could not load dashboards</h3>
            <p className="mt-2 text-sm text-slate-400">
              {error instanceof ApiError ? error.message : 'Something went wrong while loading dashboards.'}
            </p>
            <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
              {isFetching ? 'Retrying…' : 'Retry'}
            </Button>
          </div>
        ) : orderedDashboards.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-8 text-center">
            <p className="text-sm text-slate-300">No dashboards yet.</p>
            <Button asChild variant="secondary" className="mt-4">
              <Link to="/app/dashboards">Create your first dashboard</Link>
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            {orderedDashboards.map((dashboard, index) => (
              <DashboardSection
                key={dashboard.id}
                dashboard={dashboard}
                pollInterval={DEFAULT_POLLING_INTERVAL_MS}
                index={index}
                total={orderedDashboards.length}
                isDragging={draggingId === dashboard.id}
                onDragStart={() => setDraggingId(dashboard.id)}
                onDragEnd={() => setDraggingId(null)}
                onDragOver={handleDragOver}
                onDrop={() => handleDrop(index)}
                onMoveUp={() => moveBy(dashboard.id, -1)}
                onMoveDown={() => moveBy(dashboard.id, 1)}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
