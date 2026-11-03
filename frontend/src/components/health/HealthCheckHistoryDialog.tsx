import { Fragment, useMemo, useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { ApiError } from '../../api/client'
import type { HealthCheckConfig, StepExecutionStatus } from '../../api/healthchecks'
import { useHealthCheckResultsQuery } from '../../api/healthchecks'
import { computeUptimePercent } from '../../features/health/status'
import { formatDateTime } from '../../lib/datetime'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../ui/dialog'
import { Skeleton } from '../ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'
import { HealthStatusBadge } from './HealthStatusBadge'

const HISTORY_LIMIT = 50

const STEP_STATUS_VARIANT: Record<StepExecutionStatus, 'success' | 'danger' | 'muted'> = {
  PASSED: 'success',
  FAILED: 'danger',
  SKIPPED: 'muted',
}

const STEP_STATUS_LABEL: Record<StepExecutionStatus, string> = {
  PASSED: 'Passed',
  FAILED: 'Failed',
  SKIPPED: 'Skipped',
}

interface HealthCheckHistoryDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  healthCheck: HealthCheckConfig | null
}

export function HealthCheckHistoryDialog({ open, onOpenChange, healthCheck }: HealthCheckHistoryDialogProps) {
  const configId = open && healthCheck ? healthCheck.id : null
  const { data, isLoading, isError, error, refetch, isFetching } = useHealthCheckResultsQuery(
    configId,
    HISTORY_LIMIT,
  )
  const [expandedId, setExpandedId] = useState<number | null>(null)

  const results = useMemo(() => data ?? [], [data])
  const uptimePercent = useMemo(() => computeUptimePercent(results, new Date()), [results])
  const isMultiStep = healthCheck?.type === 'MULTI_STEP_API'

  const toggleExpanded = (id: number) => {
    setExpandedId((current) => (current === id ? null : id))
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>{healthCheck?.name ?? 'Health check'} — history</DialogTitle>
          <DialogDescription>
            Last {HISTORY_LIMIT} results
            {healthCheck ? (
              <>
                {' · '}
                {isMultiStep ? (
                  <span className="text-slate-500">
                    {healthCheck.steps?.length ?? 0}-step workflow:{' '}
                    {healthCheck.steps?.map((step) => step.name).join(' → ')}
                  </span>
                ) : (
                  <span className="break-all text-slate-500">{healthCheck.targetUrl}</span>
                )}
              </>
            ) : null}
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2 rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5 text-sm">
          <div className="flex items-center gap-2">
            <span className="text-slate-400">24h uptime:</span>
            <span className="font-medium text-slate-100">
              {uptimePercent === null ? 'No data' : `${uptimePercent.toFixed(1)}%`}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-slate-400">Results loaded:</span>
            <span className="font-medium text-slate-100">{results.length}</span>
          </div>
        </div>

        <div className="mt-4 max-h-[55vh] overflow-y-auto">
          {isLoading ? (
            <div className="space-y-2" aria-busy="true">
              {[0, 1, 2, 3].map((row) => (
                <Skeleton key={row} className="h-10 w-full rounded-lg" />
              ))}
            </div>
          ) : isError ? (
            <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/5 p-4">
              <p className="text-sm text-slate-300">
                {error instanceof ApiError ? error.message : 'Could not load the check history.'}
              </p>
              <Button type="button" variant="outline" className="mt-3" onClick={() => refetch()} disabled={isFetching}>
                {isFetching ? 'Retrying…' : 'Retry'}
              </Button>
            </div>
          ) : results.length === 0 ? (
            <div className="rounded-xl border border-dashed border-white/10 bg-panel/40 p-8 text-center">
              <p className="text-sm text-slate-300">No results yet.</p>
              <p className="mt-1 text-xs text-slate-500">
                Results appear after the first scheduled check, which runs within one interval.
              </p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Checked at</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Response time</TableHead>
                  <TableHead>Error message</TableHead>
                  <TableHead className="text-right">Steps</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {results.map((result) => {
                  const stepResults = result.stepResults ?? []
                  const hasSteps = stepResults.length > 0
                  const expanded = expandedId === result.id
                  return (
                    <Fragment key={result.id}>
                      <TableRow>
                        <TableCell className="whitespace-nowrap">{formatDateTime(result.checkedAt)}</TableCell>
                        <TableCell>
                          <HealthStatusBadge status={result.status} />
                        </TableCell>
                        <TableCell className="whitespace-nowrap">{result.responseTimeMs} ms</TableCell>
                        <TableCell className="max-w-xs break-words text-slate-400">
                          {result.errorMessage ? result.errorMessage : '—'}
                        </TableCell>
                        <TableCell className="text-right">
                          {hasSteps ? (
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              aria-expanded={expanded}
                              aria-label={`${expanded ? 'Hide' : 'Show'} step details for result ${result.id}`}
                              onClick={() => toggleExpanded(result.id)}
                            >
                              {expanded ? (
                                <ChevronUp className="mr-1 h-4 w-4" aria-hidden="true" />
                              ) : (
                                <ChevronDown className="mr-1 h-4 w-4" aria-hidden="true" />
                              )}
                              {stepResults.length}
                            </Button>
                          ) : (
                            '—'
                          )}
                        </TableCell>
                      </TableRow>
                      {expanded && hasSteps ? (
                        <TableRow>
                          <TableCell colSpan={5} className="bg-white/[0.02]">
                            <div className="space-y-2 py-1">
                              <p className="text-xs font-medium text-slate-300">Step results</p>
                              <Table>
                                <TableHeader>
                                  <TableRow>
                                    <TableHead>Step</TableHead>
                                    <TableHead>Status</TableHead>
                                    <TableHead>HTTP</TableHead>
                                    <TableHead>Time</TableHead>
                                    <TableHead>Detail</TableHead>
                                  </TableRow>
                                </TableHeader>
                                <TableBody>
                                  {stepResults.map((step, index) => (
                                    <TableRow key={`${result.id}-${index}`}>
                                      <TableCell className="text-slate-200">{step.name}</TableCell>
                                      <TableCell>
                                        <Badge variant={STEP_STATUS_VARIANT[step.status]}>
                                          {STEP_STATUS_LABEL[step.status]}
                                        </Badge>
                                      </TableCell>
                                      <TableCell>{step.statusCode ?? '—'}</TableCell>
                                      <TableCell>{step.responseTimeMs} ms</TableCell>
                                      <TableCell className="max-w-xs break-words text-slate-400">
                                        {step.errorMessage ?? '—'}
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </div>
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </Fragment>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
