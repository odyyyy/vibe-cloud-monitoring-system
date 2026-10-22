import { useMemo, useState } from 'react'
import { Bell, Pencil, Plus, Trash2, Webhook } from 'lucide-react'
import { toast } from 'sonner'
import type { AlertRule } from '../../api/alerts'
import { useAlertRulesQuery, useDeleteAlertRuleMutation, useUpdateAlertRuleMutation } from '../../api/alerts'
import { ApiError } from '../../api/client'
import { useHealthChecksListQuery } from '../../api/healthchecks'
import { useApplicationsQuery } from '../../api/useApplications'
import { AlertRuleFormDialog } from '../../components/alerts/AlertRuleFormDialog'
import { MaintenanceStatusBanner } from '../../components/alerts/MaintenanceStatusBanner'
import { NotificationChannelsDialog } from '../../components/alerts/NotificationChannelsDialog'
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
  alertConditionSymbol,
  alertRuleTypeLabel,
  logRuleSummary,
  logicOperatorLabel,
} from '../../features/alerts/labels'
import { metricLabel } from '../../features/metrics/catalog'
import { useCanManage } from '../../features/organizations/useCanManage'

export default function AlertsPage() {
  const canManage = useCanManage()
  const rulesQuery = useAlertRulesQuery()
  const applicationsQuery = useApplicationsQuery()
  const healthChecksQuery = useHealthChecksListQuery()
  const updateMutation = useUpdateAlertRuleMutation()
  const deleteMutation = useDeleteAlertRuleMutation()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<AlertRule | null>(null)
  const [channelsOpen, setChannelsOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<AlertRule | null>(null)

  const rules = rulesQuery.data ?? []

  const applicationNames = useMemo(() => {
    const map = new Map<number, string>()
    for (const application of applicationsQuery.data ?? []) {
      map.set(application.id, application.name)
    }
    return map
  }, [applicationsQuery.data])

  const healthCheckNames = useMemo(() => {
    const map = new Map<number, string>()
    for (const check of healthChecksQuery.data ?? []) {
      map.set(check.id, check.name)
    }
    return map
  }, [healthChecksQuery.data])

  const describeScope = (rule: AlertRule): string => {
    if (rule.type === 'HEALTH_CHECK') {
      const checkId = Number(rule.target)
      return healthCheckNames.get(checkId) ?? `Health check #${rule.target}`
    }
    const appName = rule.applicationId === null ? 'Unknown application' : applicationNames.get(rule.applicationId) ?? `Application #${rule.applicationId}`
    if (rule.type === 'LOG') {
      return `${appName} - Logs`
    }
    return `${appName} - ${metricLabel(rule.target)}`
  }

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (rule: AlertRule) => {
    setEditing(rule)
    setFormOpen(true)
  }

  const handleToggle = async (rule: AlertRule, next: boolean) => {
    try {
      await updateMutation.mutateAsync({ id: rule.id, data: { isActive: next } })
      toast.success(next ? 'Alert rule enabled.' : 'Alert rule paused.')
    } catch (toggleError) {
      toast.error(toggleError instanceof ApiError ? toggleError.message : 'Could not update the alert rule.')
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success('Alert rule deleted.')
      setDeleteTarget(null)
    } catch (deleteError) {
      toast.error(deleteError instanceof ApiError ? deleteError.message : 'Could not delete the alert rule.')
    }
  }

  const activeCount = rules.filter((rule) => rule.isActive).length

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Alerting</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Alerts</h1>
          <p className="text-sm text-slate-400">
            Rules that open an incident when a metric, log query, or health check breaches.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canManage ? (
            <>
              <Button type="button" variant="secondary" onClick={() => setChannelsOpen(true)}>
                <Webhook className="h-4 w-4" aria-hidden="true" />
                Notification channels
              </Button>
              <Button type="button" onClick={openCreate}>
                <Plus className="h-4 w-4" aria-hidden="true" />
                Create rule
              </Button>
            </>
          ) : null}
        </div>
      </header>

      <MaintenanceStatusBanner />

      {rulesQuery.isLoading ? (
        <AlertsSkeleton />
      ) : rulesQuery.isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load alert rules</h2>
          <p className="mt-2 text-sm text-slate-400">
            {rulesQuery.error instanceof ApiError ? rulesQuery.error.message : 'Something went wrong while loading alert rules.'}
          </p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={() => rulesQuery.refetch()}
            disabled={rulesQuery.isFetching}
          >
            {rulesQuery.isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : rules.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
          <p className="text-sm text-slate-300">
            {canManage ? 'No alert rules yet. Create your first rule.' : 'No alert rules in this organization yet.'}
          </p>
          {canManage ? (
            <Button type="button" variant="secondary" className="mt-4" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create rule
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2" role="status" aria-label="Alert rule summary">
            <Badge variant="success">{activeCount} active</Badge>
            <Badge variant="muted">{rules.length - activeCount} paused</Badge>
          </div>

          <div className="rounded-2xl border border-white/10 bg-panel/40 p-1">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Scope</TableHead>
                  <TableHead>Condition</TableHead>
                  <TableHead>Duration</TableHead>
                  <TableHead>Active</TableHead>
                  {canManage ? <TableHead className="text-right">Actions</TableHead> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {rules.map((rule) => (
                  <TableRow key={rule.id}>
                    <TableCell className="font-medium text-slate-100">{rule.name}</TableCell>
                    <TableCell>
                      <Badge variant="muted">{alertRuleTypeLabel(rule.type)}</Badge>
                    </TableCell>
                    <TableCell className="text-slate-400">{describeScope(rule)}</TableCell>
                    <TableCell className="whitespace-nowrap">
                      {rule.type === 'HEALTH_CHECK'
                        ? 'Down or Timeout'
                        : rule.type === 'LOG'
                          ? logRuleSummary(rule)
                          : rule.conditions.length > 1
                            ? `${rule.conditions.length} conditions / ${logicOperatorLabel(rule.logicOperator)}`
                            : `${alertConditionSymbol(rule.condition)} ${rule.threshold}`}
                    </TableCell>
                    <TableCell className="whitespace-nowrap">{`${rule.durationMinutes} min`}</TableCell>
                    <TableCell>
                      {canManage ? (
                        <Switch
                          checked={rule.isActive}
                          onCheckedChange={(next) => void handleToggle(rule, next)}
                          disabled={updateMutation.isPending}
                          aria-label={rule.isActive ? `Pause ${rule.name}` : `Enable ${rule.name}`}
                        />
                      ) : (
                        <Badge variant={rule.isActive ? 'success' : 'muted'}>
                          {rule.isActive ? 'Active' : 'Paused'}
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
                            aria-label={`Edit ${rule.name}`}
                            onClick={() => openEdit(rule)}
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="text-red-300 hover:text-red-200"
                            aria-label={`Delete ${rule.name}`}
                            onClick={() => setDeleteTarget(rule)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    ) : null}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}

      <AlertRuleFormDialog open={formOpen} onOpenChange={setFormOpen} rule={editing} />
      <NotificationChannelsDialog open={channelsOpen} onOpenChange={setChannelsOpen} />
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
            <AlertDialogTitle>Delete alert rule</AlertDialogTitle>
            <AlertDialogDescription>
              This removes “{deleteTarget?.name}”. Its incident history is deleted with it. This action cannot be undone.
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

function AlertsSkeleton() {
  return (
    <div className="space-y-4" aria-busy="true">
      <div className="flex gap-2">
        <Skeleton className="h-6 w-20 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
      <div className="space-y-2 rounded-2xl border border-white/10 bg-panel/40 p-4">
        {[0, 1, 2].map((row) => (
          <div key={row} className="flex items-center gap-4">
            <Bell className="h-4 w-4 text-slate-600" aria-hidden="true" />
            <Skeleton className="h-4 flex-1" />
            <Skeleton className="h-6 w-20 rounded-full" />
          </div>
        ))}
      </div>
    </div>
  )
}
