import { AlertCircle, CheckCircle2, RotateCw, Send } from 'lucide-react'
import type { ScheduledReport, ScheduledReportPreview } from '../../api/scheduledReports'
import { ApiError } from '../../api/client'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog'
import { Skeleton } from '../ui/skeleton'
import { formatDateTime } from '../../lib/datetime'
import { scheduledReportFrequencyLabel } from '../../features/reports/schemas'

interface ReportPreviewDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  report: ScheduledReport | null
  preview: ScheduledReportPreview | null
  isPending: boolean
  error: unknown
  onRetry: () => void
}

function deliveryVariant(status: ScheduledReportPreview['deliveryStatus']) {
  return status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
}

export function ReportPreviewDialog({
  open,
  onOpenChange,
  report,
  preview,
  isPending,
  error,
  onRetry,
}: ReportPreviewDialogProps) {
  const content = preview?.content

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Test report</DialogTitle>
          <DialogDescription>
            Sends a real test message for {report?.name ?? 'this report'} and records a delivery
            attempt. Content covers the report&apos;s configured period.
          </DialogDescription>
        </DialogHeader>

        {isPending ? (
          <div className="mt-6 space-y-3" aria-busy="true" aria-live="polite">
            <p className="text-sm text-slate-300">Generating the report and sending a test...</p>
            <Skeleton className="h-16 w-full rounded-xl" />
            <Skeleton className="h-40 w-full rounded-xl" />
          </div>
        ) : error ? (
          <div role="alert" className="mt-6 rounded-xl border border-red-500/30 bg-red-500/5 p-4">
            <h3 className="text-sm font-medium text-red-200">Could not send the test report</h3>
            <p className="mt-1 text-xs text-slate-400">
              {error instanceof ApiError ? error.message : 'Something went wrong.'}
            </p>
          </div>
        ) : preview && content ? (
          <div className="mt-6 space-y-5">
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-white/10 bg-white/[0.02] p-4">
              {preview.deliveryStatus === 'SUCCESS' ? (
                <CheckCircle2 className="h-5 w-5 text-emerald-300" aria-hidden="true" />
              ) : (
                <AlertCircle className="h-5 w-5 text-amber-300" aria-hidden="true" />
              )}
              <Badge variant={deliveryVariant(preview.deliveryStatus)}>
                {preview.deliveryStatus === 'SUCCESS'
                  ? 'Delivered'
                  : preview.deliveryStatus === 'FAILED'
                    ? 'Delivery failed'
                    : 'Pending'}
              </Badge>
              <p className="min-w-0 flex-1 text-sm text-slate-300">{preview.deliveryMessage}</p>
              {preview.lastHttpStatus !== null ? (
                <span className="text-xs text-slate-500">HTTP {preview.lastHttpStatus}</span>
              ) : null}
            </div>

            <section className="space-y-2">
              <h3 className="font-display text-base font-semibold text-slate-100">
                {content.reportName}
              </h3>
              <p className="text-xs text-slate-500">
                {scheduledReportFrequencyLabel(content.frequency)} ·{' '}
                {formatDateTime(content.from)} to {formatDateTime(content.to)} · generated{' '}
                {formatDateTime(content.generatedAt)}
              </p>
              <p className="text-xs text-slate-400">
                {content.applicationCount} applications · {content.healthCheckCount} health checks
              </p>
            </section>

            {content.uptime ? (
              <section className="rounded-xl border border-white/10 bg-panel/40 p-4">
                <h4 className="text-sm font-medium text-slate-100">Uptime</h4>
                <p className="mt-2 font-display text-2xl text-slate-100">
                  {content.uptime.percentage === null
                    ? 'No data'
                    : `${content.uptime.percentage.toFixed(2)}%`}
                </p>
                <p className="text-xs text-slate-500">
                  {content.uptime.upChecks.toLocaleString()} up of{' '}
                  {content.uptime.totalChecks.toLocaleString()} checks
                </p>
              </section>
            ) : null}

            {content.errors ? (
              <section className="rounded-xl border border-white/10 bg-panel/40 p-4">
                <h4 className="text-sm font-medium text-slate-100">
                  Top errors · {content.errors.totalErrors.toLocaleString()} total
                </h4>
                {content.errors.topErrors.length === 0 ? (
                  <p className="mt-2 text-xs text-slate-500">No error logs in this period.</p>
                ) : (
                  <ul className="mt-3 space-y-2">
                    {content.errors.topErrors.map((entry) => (
                      <li
                        key={`${entry.message}-${entry.count}`}
                        className="flex items-start justify-between gap-3 text-xs"
                      >
                        <span className="min-w-0 break-all text-slate-300">{entry.message}</span>
                        <span className="shrink-0 text-slate-500">
                          {entry.count.toLocaleString()}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            ) : null}

            {content.metrics ? (
              <section className="rounded-xl border border-white/10 bg-panel/40 p-4">
                <h4 className="text-sm font-medium text-slate-100">
                  Metrics · {content.metrics.totalPoints.toLocaleString()} points
                </h4>
                {content.metrics.topMetrics.length === 0 ? (
                  <p className="mt-2 text-xs text-slate-500">No metric samples in this period.</p>
                ) : (
                  <ul className="mt-3 space-y-2">
                    {content.metrics.topMetrics.map((entry) => (
                      <li
                        key={entry.name}
                        className="flex items-start justify-between gap-3 text-xs"
                      >
                        <span className="min-w-0 break-all text-slate-300">{entry.name}</span>
                        <span className="shrink-0 text-slate-500">
                          {entry.count.toLocaleString()}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            ) : null}

            {content.incidents ? (
              <section className="rounded-xl border border-white/10 bg-panel/40 p-4">
                <h4 className="text-sm font-medium text-slate-100">
                  Incidents · {content.incidents.totalIncidents.toLocaleString()} total,{' '}
                  {content.incidents.unresolvedIncidents.toLocaleString()} unresolved
                </h4>
                {content.incidents.latest.length === 0 ? (
                  <p className="mt-2 text-xs text-slate-500">No incidents in this period.</p>
                ) : (
                  <ul className="mt-3 space-y-2">
                    {content.incidents.latest.map((incident) => (
                      <li
                        key={`${incident.ruleName}-${incident.triggeredAt}`}
                        className="flex items-start justify-between gap-3 text-xs"
                      >
                        <span className="min-w-0 break-all text-slate-300">
                          {incident.ruleName}
                        </span>
                        <span className="shrink-0 text-slate-500">
                          {incident.status} · {formatDateTime(incident.triggeredAt)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            ) : null}

            <details className="rounded-xl border border-white/10 bg-white/[0.02] p-4">
              <summary className="cursor-pointer text-sm font-medium text-slate-200">
                Exact {preview.channelType === 'EMAIL' ? 'email' : 'Slack'} payload
              </summary>
              {preview.channelType === 'EMAIL' ? (
                <div className="mt-3 space-y-3">
                  <p className="text-xs text-slate-400">Subject: {preview.subject}</p>
                  <pre className="max-h-80 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-black/30 p-3 text-xs text-slate-300">
                    {preview.html}
                  </pre>
                </div>
              ) : (
                <pre className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-black/30 p-3 text-xs text-slate-300">
                  {JSON.stringify(preview.slackMessage, null, 2)}
                </pre>
              )}
            </details>
          </div>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button type="button" onClick={onRetry} disabled={isPending}>
            {isPending ? (
              <RotateCw className="h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <Send className="h-4 w-4" aria-hidden="true" />
            )}
            {isPending ? 'Sending...' : 'Send again'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
