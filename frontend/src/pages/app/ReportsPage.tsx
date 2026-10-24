import { useMemo, useState } from 'react'
import { CalendarClock, Pencil, Plus, Send, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { useNotificationChannelsQuery } from '../../api/alerts'
import { ApiError } from '../../api/client'
import type { ScheduledReport } from '../../api/scheduledReports'
import {
  useDeleteScheduledReportMutation,
  useScheduledReportsQuery,
  useTestScheduledReportMutation,
} from '../../api/scheduledReports'
import { ScheduledReportFormDialog } from '../../components/reports/ScheduledReportFormDialog'
import { ReportPreviewDialog } from '../../components/reports/ReportPreviewDialog'
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { useCanManage } from '../../features/organizations/useCanManage'
import {
  scheduledReportChannelLabel,
  scheduledReportFrequencyLabel,
  scheduledReportSchedule,
} from '../../features/reports/schemas'
import { formatDateTime, parseUtcInstant } from '../../lib/datetime'

function recipientSummary(recipients: string[]): string {
  if (recipients.length === 0) {
    return 'No recipients'
  }
  if (recipients.length <= 2) {
    return recipients.join(', ')
  }
  return `${recipients.slice(0, 2).join(', ')} +${recipients.length - 2} more`
}

export default function ReportsPage() {
  const canManage = useCanManage()
  const reportsQuery = useScheduledReportsQuery()
  const channelsQuery = useNotificationChannelsQuery()
  const deleteMutation = useDeleteScheduledReportMutation()
  const testMutation = useTestScheduledReportMutation()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<ScheduledReport | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ScheduledReport | null>(null)
  const [previewTarget, setPreviewTarget] = useState<ScheduledReport | null>(null)

  const reports = useMemo(() => reportsQuery.data ?? [], [reportsQuery.data])
  const sortedReports = useMemo(
    () =>
      [...reports].sort((first, second) => {
        const firstNext = first.nextRunAt ? parseUtcInstant(first.nextRunAt)?.getTime() ?? 0 : 0
        const secondNext = second.nextRunAt
          ? parseUtcInstant(second.nextRunAt)?.getTime() ?? 0
          : 0
        if (firstNext !== secondNext) {
          return firstNext - secondNext
        }
        return first.name.localeCompare(second.name)
      }),
    [reports],
  )

  const channelNames = useMemo(() => {
    const map = new Map<number, string>()
    for (const channel of channelsQuery.data ?? []) {
      map.set(channel.id, `Slack channel #${channel.id}`)
    }
    return map
  }, [channelsQuery.data])

  const emailCount = reports.filter((report) => report.channelType === 'EMAIL').length
  const slackCount = reports.filter((report) => report.channelType === 'SLACK').length

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (report: ScheduledReport) => {
    setEditing(report)
    setFormOpen(true)
  }

  const startTest = (report: ScheduledReport) => {
    setPreviewTarget(report)
    testMutation.reset()
    testMutation.mutate(report.id)
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success('Scheduled report deleted.')
      setDeleteTarget(null)
    } catch (error) {
      toast.error(
        error instanceof ApiError ? error.message : 'Could not delete the scheduled report.',
      )
    }
  }

  const previewIsCurrent =
    previewTarget !== null && testMutation.variables === previewTarget.id
  const preview = previewIsCurrent ? testMutation.data ?? null : null
  const previewError = previewIsCurrent ? testMutation.error : null
  const previewPending = previewIsCurrent && testMutation.isPending

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Alerting</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Reports</h1>
          <p className="text-sm text-slate-400">
            Recurring email and Slack digests of uptime, top errors, metrics and recent incidents.
          </p>
        </div>
        {canManage ? (
          <Button type="button" onClick={openCreate} disabled={reports.length >= 10}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create report
          </Button>
        ) : null}
      </header>

      {reportsQuery.isLoading ? (
        <ReportsSkeleton />
      ) : reportsQuery.isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">
            Could not load scheduled reports
          </h2>
          <p className="mt-2 text-sm text-slate-400">
            {reportsQuery.error instanceof ApiError
              ? reportsQuery.error.message
              : 'Something went wrong while loading scheduled reports.'}
          </p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={() => reportsQuery.refetch()}
            disabled={reportsQuery.isFetching}
          >
            {reportsQuery.isFetching ? 'Retrying...' : 'Retry'}
          </Button>
        </div>
      ) : reports.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
          <CalendarClock className="mx-auto h-6 w-6 text-slate-600" aria-hidden="true" />
          <p className="mt-3 text-sm text-slate-300">
            {canManage
              ? 'No scheduled reports yet. Create one to receive a regular operations digest.'
              : 'No scheduled reports in this organization yet.'}
          </p>
          {canManage ? (
            <Button type="button" variant="secondary" className="mt-4" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create report
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2" role="status" aria-label="Report summary">
            <Badge variant="muted">{reports.length} of 10 reports</Badge>
            <Badge variant={emailCount > 0 ? 'info' : 'muted'}>{emailCount} email</Badge>
            <Badge variant={slackCount > 0 ? 'info' : 'muted'}>{slackCount} Slack</Badge>
            {reports.length >= 10 ? (
              <Badge variant="warning">Report limit reached</Badge>
            ) : null}
          </div>

          <div className="rounded-2xl border border-white/10 bg-panel/40 p-1">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Schedule</TableHead>
                  <TableHead>Destination</TableHead>
                  <TableHead>Sections</TableHead>
                  <TableHead>Next run</TableHead>
                  <TableHead>Last run</TableHead>
                  {canManage ? <TableHead className="text-right">Actions</TableHead> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {sortedReports.map((report) => {
                  const deleting =
                    deleteMutation.isPending && deleteMutation.variables === report.id
                  const testing =
                    testMutation.isPending && testMutation.variables === report.id
                  const sections = [
                    report.includeUptime ? 'Uptime' : null,
                    report.includeTopErrors ? 'Errors' : null,
                    report.includeMetrics ? 'Metrics' : null,
                  ].filter((section): section is string => section !== null)

                  return (
                    <TableRow key={report.id}>
                      <TableCell className="font-medium text-slate-100">{report.name}</TableCell>
                      <TableCell className="min-w-[210px] text-slate-300">
                        <div>{scheduledReportFrequencyLabel(report.frequency)}</div>
                        <div className="text-xs text-slate-500">
                          {scheduledReportSchedule(
                            report.frequency,
                            report.dayOfWeek,
                            report.timeOfDay,
                            report.timeZone,
                          )}
                        </div>
                      </TableCell>
                      <TableCell className="max-w-[240px] text-slate-300">
                        <div>{scheduledReportChannelLabel(report.channelType)}</div>
                        <div className="break-all text-xs text-slate-500">
                          {report.channelType === 'EMAIL'
                            ? recipientSummary(report.recipients)
                            : report.channelId === null
                              ? 'No channel'
                              : channelNames.get(report.channelId) ??
                                `Slack channel #${report.channelId}`}
                        </div>
                      </TableCell>
                      <TableCell className="text-xs text-slate-400">
                        {sections.length > 0 ? sections.join(' · ') : 'No sections'}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-slate-300">
                        {report.nextRunAt ? formatDateTime(report.nextRunAt) : 'Not scheduled'}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-slate-400">
                        {report.lastRunAt ? formatDateTime(report.lastRunAt) : 'Not run yet'}
                      </TableCell>
                      {canManage ? (
                        <TableCell>
                          <div className="flex justify-end gap-1">
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              aria-label={`Send test report for ${report.name}`}
                              title="Send test report"
                              onClick={() => startTest(report)}
                              disabled={testing}
                            >
                              <Send className="h-4 w-4" aria-hidden="true" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              aria-label={`Edit ${report.name}`}
                              title="Edit report"
                              onClick={() => openEdit(report)}
                            >
                              <Pencil className="h-4 w-4" aria-hidden="true" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              aria-label={`Delete ${report.name}`}
                              title="Delete report"
                              onClick={() => setDeleteTarget(report)}
                              disabled={deleting}
                            >
                              <Trash2 className="h-4 w-4" aria-hidden="true" />
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

          <p className="text-xs text-slate-500">
            Schedules run in each report&apos;s configured time zone. Up to 10 reports are allowed
            per organization.
          </p>
        </>
      )}

      <ScheduledReportFormDialog open={formOpen} onOpenChange={setFormOpen} report={editing} />

      <ReportPreviewDialog
        open={previewTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPreviewTarget(null)
          }
        }}
        report={previewTarget}
        preview={preview}
        isPending={previewPending}
        error={previewError}
        onRetry={() => {
          if (previewTarget) {
            startTest(previewTarget)
          }
        }}
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
            <AlertDialogTitle>Delete scheduled report?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteTarget?.name} will stop sending. Delivery history is kept for auditing.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={(event) => {
                event.preventDefault()
                void handleDelete()
              }}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? 'Deleting...' : 'Delete report'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}

function ReportsSkeleton() {
  return (
    <div className="space-y-3" aria-busy="true">
      <Skeleton className="h-8 w-64 rounded" />
      <Skeleton className="h-64 w-full rounded-2xl" />
    </div>
  )
}
