import { useState } from 'react'
import { Activity, History, Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { HealthCheckConfig } from '../../api/healthchecks'
import {
  useDeleteHealthCheckMutation,
  useHealthChecksOverviewQuery,
  useUpdateHealthCheckMutation,
} from '../../api/healthchecks'
import { HealthCheckFormDialog } from '../../components/health/HealthCheckFormDialog'
import { HealthCheckHistoryDialog } from '../../components/health/HealthCheckHistoryDialog'
import { HealthStatusBadge } from '../../components/health/HealthStatusBadge'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../../components/ui/alert-dialog'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import { Switch } from '../../components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { summarizeHealthChecks } from '../../features/health/status'
import { formatDateTime } from '../../lib/datetime'
import { useDocumentVisible } from '../../lib/useDocumentVisible'

const REFRESH_INTERVAL_MS = 30_000

export default function HealthChecksPage() {
  const isVisible = useDocumentVisible()
  const refetchInterval = isVisible ? REFRESH_INTERVAL_MS : false
  const { data, isLoading, isError, error, refetch, isFetching } = useHealthChecksOverviewQuery(refetchInterval)

  const updateMutation = useUpdateHealthCheckMutation()
  const deleteMutation = useDeleteHealthCheckMutation()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HealthCheckConfig | null>(null)
  const [historyTarget, setHistoryTarget] = useState<HealthCheckConfig | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<HealthCheckConfig | null>(null)

  const overview = data ?? []
  const summary = summarizeHealthChecks(overview)

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (check: HealthCheckConfig) => {
    setEditing(check)
    setFormOpen(true)
  }

  const handleToggle = async (check: HealthCheckConfig, next: boolean) => {
    try {
      await updateMutation.mutateAsync({ id: check.id, data: { isActive: next } })
      toast.success(next ? 'Health check enabled.' : 'Health check paused.')
    } catch (toggleError) {
      toast.error(toggleError instanceof ApiError ? toggleError.message : 'Could not update the health check.')
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success('Health check deleted.')
      setDeleteTarget(null)
    } catch (deleteError) {
      toast.error(deleteError instanceof ApiError ? deleteError.message : 'Could not delete the health check.')
    }
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Health</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Health Checks</h1>
          <p className="text-sm text-slate-400">
            Synthetic HTTP checks the platform runs against your endpoints.
          </p>
        </div>
        <Button type="button" onClick={openCreate}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          Create check
        </Button>
      </header>

      {isLoading ? (
        <HealthChecksSkeleton />
      ) : isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load health checks</h2>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError ? error.message : 'Something went wrong while loading health checks.'}
          </p>
          <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : overview.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-[#0b101b]/40 p-10 text-center">
          <p className="text-sm text-slate-300">No health checks yet. Create your first check.</p>
          <Button type="button" variant="secondary" className="mt-4" onClick={openCreate}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create check
          </Button>
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2" role="status" aria-label="Health check summary">
            <Badge variant="success">{summary.up} up</Badge>
            <Badge variant="danger">{summary.down} down</Badge>
            {summary.timeout > 0 ? <Badge variant="warning">{summary.timeout} timeout</Badge> : null}
            <Badge variant="muted">{summary.unknown} unknown</Badge>
            <span className="ml-1 text-xs text-slate-500">
              {isVisible ? 'Auto-refreshes every 30 seconds.' : 'Auto-refresh paused while this tab is hidden.'}
            </span>
          </div>

          <div className="rounded-2xl border border-white/10 bg-[#0b101b]/40 p-1">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Target URL</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last check</TableHead>
                  <TableHead>Response time</TableHead>
                  <TableHead>Active</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {overview.map(({ check, latest }) => (
                  <TableRow
                    key={check.id}
                    className="cursor-pointer"
                    onClick={() => setHistoryTarget(check)}
                  >
                    <TableCell className="font-medium text-slate-100">{check.name}</TableCell>
                    <TableCell className="max-w-xs truncate text-slate-400" title={check.targetUrl}>
                      {check.targetUrl}
                    </TableCell>
                    <TableCell>
                      <HealthStatusBadge status={latest?.status ?? null} />
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      {latest ? formatDateTime(latest.checkedAt) : 'Never'}
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      {latest ? `${latest.responseTimeMs} ms` : '—'}
                    </TableCell>
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      <Switch
                        checked={check.isActive}
                        onCheckedChange={(next) => void handleToggle(check, next)}
                        disabled={updateMutation.isPending}
                        aria-label={check.isActive ? `Pause ${check.name}` : `Enable ${check.name}`}
                      />
                    </TableCell>
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label={`View history for ${check.name}`}
                          onClick={() => setHistoryTarget(check)}
                        >
                          <History className="h-4 w-4" />
                        </Button>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label={`Edit ${check.name}`}
                          onClick={() => openEdit(check)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="text-red-300 hover:text-red-200"
                          aria-label={`Delete ${check.name}`}
                          onClick={() => setDeleteTarget(check)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}

      <HealthCheckFormDialog open={formOpen} onOpenChange={setFormOpen} healthCheck={editing} />
      <HealthCheckHistoryDialog
        open={historyTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setHistoryTarget(null)
          }
        }}
        healthCheck={historyTarget}
      />
      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete health check</AlertDialogTitle>
            <AlertDialogDescription>
              This removes “{deleteTarget?.name}” and all of its recorded results. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={(event) => {
                event.preventDefault()
                void handleDelete()
              }}
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}

function HealthChecksSkeleton() {
  return (
    <div className="space-y-4" aria-busy="true">
      <div className="flex gap-2">
        <Skeleton className="h-6 w-16 rounded-full" />
        <Skeleton className="h-6 w-16 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
      <div className="space-y-2 rounded-2xl border border-white/10 bg-[#0b101b]/40 p-4">
        {[0, 1, 2].map((row) => (
          <div key={row} className="flex items-center gap-4">
            <Activity className="h-4 w-4 text-slate-600" aria-hidden="true" />
            <Skeleton className="h-4 flex-1" />
            <Skeleton className="h-6 w-20 rounded-full" />
          </div>
        ))}
      </div>
    </div>
  )
}
