import { useEffect, useState, type FormEvent } from 'react'
import { Database, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import {
  RETENTION_RUNS_LIMIT,
  useRetentionPolicyQuery,
  useRetentionPurgeRunsQuery,
  useUpdateRetentionPolicyMutation,
  type RetentionDataType,
  type RetentionPurgeRun,
  type UpdateRetentionPolicyInput,
} from '../../api/retention'
import { ApiError } from '../../api/client'
import {
  formFromPolicy,
  isPolicyDirty,
  isPolicyReduction,
  validatePolicyForm,
  type PolicyFormState,
} from './policy'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../../components/ui/alert-dialog'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Input } from '../../components/ui/input'
import { Label } from '../../components/ui/label'
import { Skeleton } from '../../components/ui/skeleton'
import { useCanManage } from '../organizations/useCanManage'
import { formatDateTime } from '../../lib/datetime'

const EMPTY_FORM: PolicyFormState = { logs: '', metrics: '', traces: '' }

const DATA_TYPE_LABELS: Record<RetentionDataType, string> = {
  LOGS: 'Logs',
  METRICS: 'Metrics',
  TRACES: 'Traces',
}

export function RetentionSettings() {
  const canManage = useCanManage()
  const policyQuery = useRetentionPolicyQuery()
  const runsQuery = useRetentionPurgeRunsQuery()
  const updateMutation = useUpdateRetentionPolicyMutation()

  const [form, setForm] = useState<PolicyFormState>(EMPTY_FORM)
  const [pendingValues, setPendingValues] = useState<UpdateRetentionPolicyInput | null>(null)

  const policy = policyQuery.data

  useEffect(() => {
    if (policy) {
      setForm(formFromPolicy(policy))
    }
  }, [policy])

  const isDirty = policy !== null && policy !== undefined && isPolicyDirty(form, policy)

  const save = async (values: UpdateRetentionPolicyInput) => {
    try {
      await updateMutation.mutateAsync(values)
      setPendingValues(null)
      toast.success('Retention policy updated.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not update the retention policy.')
    }
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!policy || !canManage) {
      return
    }
    try {
      const values = validatePolicyForm(form, policy)
      if (isPolicyReduction(values, policy)) {
        setPendingValues(values)
        return
      }
      void save(values)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Check the retention values.')
    }
  }

  const updateField = (field: keyof PolicyFormState, value: string) => {
    setForm((current) => ({ ...current, [field]: value }))
  }

  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
        <header className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="flex items-center gap-2 font-display text-lg font-semibold text-slate-100">
              <Database className="h-5 w-5 text-cyan-300" aria-hidden="true" />
              Data retention
            </h2>
            <p className="mt-1 max-w-2xl text-sm text-slate-400">
              Telemetry older than these windows is permanently deleted by the hourly purge job. Windows are measured in
              days and apply to the whole organization.
            </p>
          </div>
        </header>

        {policyQuery.isLoading ? (
          <div className="mt-6 space-y-3" aria-busy="true">
            {[0, 1, 2].map((row) => (
              <Skeleton key={row} className="h-16 w-full" />
            ))}
          </div>
        ) : policyQuery.isError || !policy ? (
          <div role="alert" className="mt-6 rounded-xl border border-red-500/30 bg-red-500/5 p-4">
            <p className="text-sm text-red-200">
              {policyQuery.error instanceof ApiError
                ? policyQuery.error.message
                : 'Could not load the retention policy.'}
            </p>
            <Button
              type="button"
              variant="outline"
              className="mt-3"
              onClick={() => void policyQuery.refetch()}
              disabled={policyQuery.isFetching}
            >
              {policyQuery.isFetching ? 'Retrying…' : 'Retry'}
            </Button>
          </div>
        ) : (
          <form className="mt-6 space-y-5" onSubmit={handleSubmit}>
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label htmlFor="retention-logs">Logs (days)</Label>
                <Input
                  id="retention-logs"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={policy.maxLogsRetentionDays}
                  step={1}
                  value={form.logs}
                  disabled={!canManage || updateMutation.isPending}
                  onChange={(event) => updateField('logs', event.target.value)}
                />
                <p className="text-xs text-slate-500">1–{policy.maxLogsRetentionDays} days</p>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="retention-metrics">Metrics (days)</Label>
                <Input
                  id="retention-metrics"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={policy.maxMetricsRetentionDays}
                  step={1}
                  value={form.metrics}
                  disabled={!canManage || updateMutation.isPending}
                  onChange={(event) => updateField('metrics', event.target.value)}
                />
                <p className="text-xs text-slate-500">1–{policy.maxMetricsRetentionDays} days</p>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="retention-traces">Traces (days)</Label>
                <Input
                  id="retention-traces"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={policy.maxTracesRetentionDays}
                  step={1}
                  value={form.traces}
                  disabled={!canManage || updateMutation.isPending}
                  onChange={(event) => updateField('traces', event.target.value)}
                />
                <p className="text-xs text-slate-500">1–{policy.maxTracesRetentionDays} days</p>
              </div>
            </div>

            <p className="rounded-lg border border-amber-400/20 bg-amber-400/5 px-3 py-2 text-sm text-amber-100">
              Reducing retention permanently deletes older data on the next purge run. This cannot be undone.
            </p>

            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-xs text-slate-500">
                {policy.updatedAt
                  ? `Last updated ${formatDateTime(policy.updatedAt)}.`
                  : 'Using platform defaults. No organization-specific policy has been saved yet.'}
              </p>
              {canManage ? (
                <Button type="submit" disabled={!isDirty || updateMutation.isPending}>
                  {updateMutation.isPending ? 'Saving…' : 'Save retention policy'}
                </Button>
              ) : (
                <p className="text-sm text-slate-400">Only owners and admins can change retention.</p>
              )}
            </div>
          </form>
        )}
      </div>

      <PurgeHistory runs={runsQuery.data ?? []} query={runsQuery} />

      <AlertDialog open={pendingValues !== null} onOpenChange={(open) => !open && setPendingValues(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Reduce retention?</AlertDialogTitle>
            <AlertDialogDescription>
              The next purge run will permanently delete telemetry older than the new windows. Lower values keep less
              history and cannot be undone by raising the value again.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={updateMutation.isPending}>Cancel</AlertDialogCancel>
            <Button
              type="button"
              variant="outline"
              className="border-red-500/40 bg-red-500/10 text-red-200 hover:bg-red-500/20 hover:text-red-100"
              disabled={updateMutation.isPending}
              onClick={() => {
                if (pendingValues) {
                  void save(pendingValues)
                }
              }}
            >
              {updateMutation.isPending ? 'Saving…' : 'Save reduced retention'}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

interface PurgeHistoryProps {
  runs: RetentionPurgeRun[]
  query: ReturnType<typeof useRetentionPurgeRunsQuery>
}

function PurgeHistory({ runs, query }: PurgeHistoryProps) {
  return (
    <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-display text-lg font-semibold text-slate-100">Purge history</h2>
          <p className="mt-1 text-sm text-slate-400">
            The {RETENTION_RUNS_LIMIT} most recent runs for this organization. Times are shown in your local timezone.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={() => void query.refetch()}
          disabled={query.isFetching}
          aria-label="Refresh purge history"
        >
          <RefreshCw className={`h-4 w-4 ${query.isFetching ? 'animate-spin' : ''}`} aria-hidden="true" />
          Refresh
        </Button>
      </header>

      {query.isLoading ? (
        <div className="mt-5 space-y-2" aria-busy="true">
          {[0, 1, 2, 3].map((row) => (
            <Skeleton key={row} className="h-10 w-full" />
          ))}
        </div>
      ) : query.isError ? (
        <div role="alert" className="mt-5 rounded-xl border border-red-500/30 bg-red-500/5 p-4">
          <p className="text-sm text-red-200">
            {query.error instanceof ApiError ? query.error.message : 'Could not load the purge history.'}
          </p>
        </div>
      ) : runs.length === 0 ? (
        <p className="mt-5 rounded-xl border border-dashed border-white/10 p-6 text-center text-sm text-slate-400">
          No purge runs yet. The job runs every hour.
        </p>
      ) : (
        <div className="mt-5 overflow-x-auto">
          <table className="w-full min-w-[40rem] border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-white/10 text-xs uppercase tracking-wide text-slate-500">
                <th scope="col" className="py-2 pr-4 font-medium">
                  Data type
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Started
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Rows deleted
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Status
                </th>
                <th scope="col" className="py-2 font-medium">
                  Details
                </th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <tr key={run.id} className="border-b border-white/5 last:border-0">
                  <td className="py-2 pr-4 text-slate-200">{DATA_TYPE_LABELS[run.dataType]}</td>
                  <td className="py-2 pr-4 text-slate-400">{formatDateTime(run.startedAt)}</td>
                  <td className="py-2 pr-4 text-slate-300">{run.rowsDeleted.toLocaleString()}</td>
                  <td className="py-2 pr-4">
                    <Badge variant={run.status === 'FAILED' ? 'danger' : 'success'}>
                      {run.status === 'FAILED' ? 'Failed' : 'Completed'}
                    </Badge>
                  </td>
                  <td className="py-2 text-slate-500" title={run.errorMessage ?? undefined}>
                    {run.errorMessage ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
