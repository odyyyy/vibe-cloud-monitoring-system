import { useState } from 'react'
import { Link } from 'react-router-dom'
import { LayoutGrid, Pencil, Plus, Trash2 } from 'lucide-react'
import { ApiError } from '../../api/client'
import type { Dashboard } from '../../api/dashboards'
import { useDashboardsQuery } from '../../api/dashboards'
import { DashboardFormDialog } from '../../components/dashboards/DashboardFormDialog'
import { DeleteDashboardDialog } from '../../components/dashboards/DeleteDashboardDialog'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import { formatDateTime } from '../../lib/datetime'

export default function DashboardsPage() {
  const { data, isLoading, isError, error, isFetching, refetch } = useDashboardsQuery()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Dashboard | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Dashboard | null>(null)

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openRename = (dashboard: Dashboard) => {
    setEditing(dashboard)
    setFormOpen(true)
  }

  const dashboards = data ?? []

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Dashboards</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Dashboards</h1>
          <p className="text-sm text-slate-400">Compose metrics and logs into shareable views.</p>
        </div>
        <Button type="button" onClick={openCreate}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          Create Dashboard
        </Button>
      </header>

      {isLoading ? (
        <DashboardGridSkeleton />
      ) : isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load dashboards</h2>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError ? error.message : 'Something went wrong while loading dashboards.'}
          </p>
          <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : dashboards.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-[#0b101b]/40 p-10 text-center">
          <p className="text-sm text-slate-300">No dashboards yet. Create your first dashboard.</p>
          <Button type="button" variant="secondary" className="mt-4" onClick={openCreate}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create Dashboard
          </Button>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {dashboards.map((dashboard) => (
            <DashboardCard
              key={dashboard.id}
              dashboard={dashboard}
              onRename={() => openRename(dashboard)}
              onDelete={() => setDeleteTarget(dashboard)}
            />
          ))}
        </div>
      )}

      <DashboardFormDialog open={formOpen} onOpenChange={setFormOpen} dashboard={editing} />
      <DeleteDashboardDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null)
          }
        }}
        dashboard={deleteTarget}
      />
    </section>
  )
}

interface DashboardCardProps {
  dashboard: Dashboard
  onRename: () => void
  onDelete: () => void
}

function DashboardCard({ dashboard, onRename, onDelete }: DashboardCardProps) {
  return (
    <article className="flex flex-col rounded-2xl border border-white/10 bg-[#0b101b]/60 p-4">
      <div className="flex items-start gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-white/10 bg-white/[0.03]">
          <LayoutGrid className="h-5 w-5 text-cyan-300" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="truncate font-display text-base font-semibold text-slate-100">{dashboard.name}</h2>
          <p className="mt-0.5 text-xs text-slate-500">Created {formatDateTime(dashboard.createdAt)}</p>
        </div>
      </div>

      <p className="mt-4 text-sm text-slate-400">
        {dashboard.widgets.length} {dashboard.widgets.length === 1 ? 'widget' : 'widgets'}
      </p>

      <div className="mt-4 flex items-center gap-2">
        <Button asChild type="button" size="sm" className="flex-1">
          <Link to={`/app/dashboards/${dashboard.id}`}>Open</Link>
        </Button>
        <Button type="button" variant="ghost" size="icon" aria-label="Rename dashboard" onClick={onRename}>
          <Pencil className="h-4 w-4" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="text-red-300 hover:text-red-200"
          aria-label="Delete dashboard"
          onClick={onDelete}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </article>
  )
}

function DashboardGridSkeleton() {
  return (
    <div aria-busy="true" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {[0, 1, 2].map((card) => (
        <div key={card} className="rounded-2xl border border-white/10 bg-[#0b101b]/60 p-4">
          <div className="flex items-center gap-3">
            <Skeleton className="h-10 w-10 rounded-xl" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-3 w-40" />
            </div>
          </div>
          <Skeleton className="mt-4 h-4 w-20" />
          <Skeleton className="mt-4 h-9 w-full" />
        </div>
      ))}
    </div>
  )
}
