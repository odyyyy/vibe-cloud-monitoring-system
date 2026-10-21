import { useMemo, useState } from 'react'
import { Pencil, Plus, Trash2, Wrench } from 'lucide-react'
import { toast } from 'sonner'
import { useAlertRulesQuery } from '../../api/alerts'
import { ApiError } from '../../api/client'
import type { MaintenanceWindow } from '../../api/maintenanceWindows'
import {
  useDeleteMaintenanceWindowMutation,
  useMaintenanceWindowsQuery,
  useUpdateMaintenanceWindowMutation,
} from '../../api/maintenanceWindows'
import { useApplicationsQuery } from '../../api/useApplications'
import { MaintenanceWindowFormDialog } from '../../components/alerts/MaintenanceWindowFormDialog'
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
import {
  activeMaintenanceWindows,
  maintenanceScopeLabel,
  maintenanceWindowStatus,
  type MaintenanceWindowStatus,
} from '../../features/maintenance/window'
import { useCanManage } from '../../features/organizations/useCanManage'
import { formatDateTime, parseUtcInstant } from '../../lib/datetime'
import { useDocumentVisible } from '../../lib/useDocumentVisible'

const REFRESH_INTERVAL_MS = 30_000

const STATUS_ORDER: Record<MaintenanceWindowStatus, number> = {
  ACTIVE: 0,
  UPCOMING: 1,
  ENDED: 2,
  DISABLED: 3,
}

const STATUS_LABEL: Record<MaintenanceWindowStatus, string> = {
  ACTIVE: 'Active now',
  UPCOMING: 'Upcoming',
  ENDED: 'Ended',
  DISABLED: 'Disabled',
}

const STATUS_VARIANT: Record<
  MaintenanceWindowStatus,
  'warning' | 'info' | 'muted' | 'success'
> = {
  ACTIVE: 'warning',
  UPCOMING: 'info',
  ENDED: 'muted',
  DISABLED: 'muted',
}

export default function MaintenancePage() {
  const canManage = useCanManage()
  const isVisible = useDocumentVisible()
  const windowsQuery = useMaintenanceWindowsQuery(isVisible ? REFRESH_INTERVAL_MS : false)
  const applicationsQuery = useApplicationsQuery()
  const rulesQuery = useAlertRulesQuery()
  const updateMutation = useUpdateMaintenanceWindowMutation()
  const deleteMutation = useDeleteMaintenanceWindowMutation()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<MaintenanceWindow | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<MaintenanceWindow | null>(null)

  const windows = useMemo(() => windowsQuery.data ?? [], [windowsQuery.data])
  const now = new Date()
  const activeWindows = activeMaintenanceWindows(windows, now)
  const upcomingCount = windows.filter(
    (window) => maintenanceWindowStatus(window, now) === 'UPCOMING',
  ).length

  const applicationNames = useMemo(() => {
    const map = new Map<number, string>()
    for (const application of applicationsQuery.data ?? []) {
      map.set(application.id, application.name)
    }
    return map
  }, [applicationsQuery.data])

  const ruleNames = useMemo(() => {
    const map = new Map<number, string>()
    for (const rule of rulesQuery.data ?? []) {
      map.set(rule.id, rule.name)
    }
    return map
  }, [rulesQuery.data])

  const sortedWindows = [...windows].sort((first, second) => {
    const firstStatus = maintenanceWindowStatus(first, now)
    const secondStatus = maintenanceWindowStatus(second, now)
    const byStatus = STATUS_ORDER[firstStatus] - STATUS_ORDER[secondStatus]
    if (byStatus !== 0) {
      return byStatus
    }
    const firstStart = parseUtcInstant(first.startTime)?.getTime() ?? 0
    const secondStart = parseUtcInstant(second.startTime)?.getTime() ?? 0
    return secondStart - firstStart
  })

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (window: MaintenanceWindow) => {
    setEditing(window)
    setFormOpen(true)
  }

  const handleToggle = async (window: MaintenanceWindow, next: boolean) => {
    try {
      await updateMutation.mutateAsync({ id: window.id, data: { isActive: next } })
      toast.success(next ? 'Maintenance window enabled.' : 'Maintenance window disabled.')
    } catch (toggleError) {
      toast.error(
        toggleError instanceof ApiError
          ? toggleError.message
          : 'Could not update the maintenance window.',
      )
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success('Maintenance window deleted.')
      setDeleteTarget(null)
    } catch (deleteError) {
      toast.error(
        deleteError instanceof ApiError
          ? deleteError.message
          : 'Could not delete the maintenance window.',
      )
    }
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Alerting</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Maintenance</h1>
          <p className="text-sm text-slate-400">
            Planned windows that mute alert incidents and notifications for all alerts, one
            application, or one rule.
          </p>
        </div>
        {canManage ? (
          <Button type="button" onClick={openCreate}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create window
          </Button>
        ) : null}
      </header>

      {windowsQuery.isLoading ? (
        <MaintenanceSkeleton />
      ) : windowsQuery.isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">
            Could not load maintenance windows
          </h2>
          <p className="mt-2 text-sm text-slate-400">
            {windowsQuery.error instanceof ApiError
              ? windowsQuery.error.message
              : 'Something went wrong while loading maintenance windows.'}
          </p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={() => windowsQuery.refetch()}
            disabled={windowsQuery.isFetching}
          >
            {windowsQuery.isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : windows.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
          <Wrench className="mx-auto h-6 w-6 text-slate-600" aria-hidden="true" />
          <p className="mt-3 text-sm text-slate-300">
            {canManage
              ? 'No maintenance windows yet. Create one before your next planned change.'
              : 'No maintenance windows in this organization yet.'}
          </p>
          {canManage ? (
            <Button type="button" variant="secondary" className="mt-4" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create window
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2" role="status" aria-label="Maintenance summary">
            <Badge variant={activeWindows.length > 0 ? 'warning' : 'muted'}>
              {activeWindows.length} active
            </Badge>
            <Badge variant={upcomingCount > 0 ? 'info' : 'muted'}>{upcomingCount} upcoming</Badge>
            <Badge variant="muted">{windows.length} total</Badge>
          </div>

          {activeWindows.length > 0 ? (
            <div className="rounded-2xl border border-amber-400/30 bg-amber-400/5 px-4 py-3">
              <p className="text-sm font-medium text-amber-100">Alerts are muted right now</p>
              <p className="mt-1 text-xs text-amber-200/70">
                New incidents and notifications are suppressed for:{' '}
                {activeWindows
                  .map((window) => window.name)
                  .join(', ')}
                . Incidents still resolve when a condition recovers.
              </p>
            </div>
          ) : null}

          <div className="rounded-2xl border border-white/10 bg-panel/40 p-1">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Scope</TableHead>
                  <TableHead>Starts</TableHead>
                  <TableHead>Ends</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Enabled</TableHead>
                  {canManage ? <TableHead className="text-right">Actions</TableHead> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {sortedWindows.map((window) => {
                  const status = maintenanceWindowStatus(window, now)
                  const toggling =
                    updateMutation.isPending && updateMutation.variables?.id === window.id
                  const deleting =
                    deleteMutation.isPending && deleteMutation.variables === window.id

                  return (
                    <TableRow key={window.id}>
                      <TableCell className="font-medium text-slate-100">{window.name}</TableCell>
                      <TableCell className="text-slate-400">
                        {maintenanceScopeLabel(window, applicationNames, ruleNames)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-slate-300">
                        {formatDateTime(window.startTime)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-slate-300">
                        {formatDateTime(window.endTime)}
                      </TableCell>
                      <TableCell>
                        <Badge variant={STATUS_VARIANT[status]}>{STATUS_LABEL[status]}</Badge>
                      </TableCell>
                      <TableCell>
                        {canManage ? (
                          <Switch
                            checked={window.isActive}
                            onCheckedChange={(next) => void handleToggle(window, next)}
                            disabled={toggling}
                            aria-label={
                              window.isActive
                                ? `Disable ${window.name}`
                                : `Enable ${window.name}`
                            }
                          />
                        ) : (
                          <Badge variant={window.isActive ? 'success' : 'muted'}>
                            {window.isActive ? 'Enabled' : 'Disabled'}
                          </Badge>
                        )}
                      </TableCell>
                      {canManage ? (
                        <TableCell>
                          <div className="flex items-center justify-end gap-1">
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              aria-label={`Edit ${window.name}`}
                              onClick={() => openEdit(window)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              className="text-red-300 hover:text-red-200"
                              aria-label={`Delete ${window.name}`}
                              onClick={() => setDeleteTarget(window)}
                              disabled={deleting}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </TableCell>
                      ) : null}
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        </>
      )}

      <MaintenanceWindowFormDialog open={formOpen} onOpenChange={setFormOpen} window={editing} />
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
            <AlertDialogTitle>Delete maintenance window</AlertDialogTitle>
            <AlertDialogDescription>
              This permanently removes “{deleteTarget?.name}”. Alerts will no longer be muted by
              it. This action cannot be undone.
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

function MaintenanceSkeleton() {
  return (
    <div className="space-y-4" aria-busy="true">
      <div className="flex gap-2">
        <Skeleton className="h-6 w-20 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
      <div className="space-y-2 rounded-2xl border border-white/10 bg-panel/40 p-4">
        {[0, 1, 2].map((row) => (
          <div key={row} className="flex items-center gap-4">
            <Wrench className="h-4 w-4 text-slate-600" aria-hidden="true" />
            <Skeleton className="h-4 flex-1" />
            <Skeleton className="h-6 w-24 rounded-full" />
          </div>
        ))}
      </div>
    </div>
  )
}
