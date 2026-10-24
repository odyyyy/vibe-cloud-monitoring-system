import { useEffect, useMemo, useState } from 'react'
import { CalendarClock, FileText } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { useNotificationChannelsQuery } from '../../api/alerts'
import { ApiError } from '../../api/client'
import type { ScheduledReport } from '../../api/scheduledReports'
import {
  useCreateScheduledReportMutation,
  useUpdateScheduledReportMutation,
} from '../../api/scheduledReports'
import { FormField } from '../auth/FormField'
import { Button } from '../ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import { Switch } from '../ui/switch'
import {
  DEFAULT_SCHEDULED_REPORT_DAY,
  DEFAULT_SCHEDULED_REPORT_TIME,
  SCHEDULED_REPORT_NAME_MAX_LENGTH,
  dayOfWeekLabel,
  scheduledReportFormSchema,
  scheduledReportFrequencyLabel,
  toScheduledReportRequest,
  type ScheduledReportFormValues,
} from '../../features/reports/schemas'

interface ScheduledReportFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  report: ScheduledReport | null
}

const WEEKDAYS = [1, 2, 3, 4, 5, 6, 7]

function initialValues(report: ScheduledReport | null): ScheduledReportFormValues {
  return {
    name: report?.name ?? '',
    frequency: report?.frequency ?? 'WEEKLY',
    dayOfWeek: report?.dayOfWeek ?? DEFAULT_SCHEDULED_REPORT_DAY,
    timeOfDay: report?.timeOfDay.slice(0, 5) ?? DEFAULT_SCHEDULED_REPORT_TIME,
    channelType: report?.channelType ?? 'EMAIL',
    channelId: report?.channelId ?? null,
    recipients: report?.recipients.join(', ') ?? '',
    includeMetrics: report?.includeMetrics ?? true,
    includeTopErrors: report?.includeTopErrors ?? true,
    includeUptime: report?.includeUptime ?? true,
  }
}

type FieldErrors = Partial<Record<keyof ScheduledReportFormValues, string>>

export function ScheduledReportFormDialog({
  open,
  onOpenChange,
  report,
}: ScheduledReportFormDialogProps) {
  const isEditing = Boolean(report)
  const createMutation = useCreateScheduledReportMutation()
  const updateMutation = useUpdateScheduledReportMutation()
  const channelsQuery = useNotificationChannelsQuery()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const [values, setValues] = useState<ScheduledReportFormValues>(() => initialValues(report))
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    setValues(initialValues(report))
    setFieldErrors({})
    setSubmitError(null)
  }, [open, report])

  const slackChannels = useMemo(
    () =>
      (channelsQuery.data ?? []).filter(
        (channel) => channel.type === 'SLACK' && channel.isActive,
      ),
    [channelsQuery.data],
  )

  const update = <Key extends keyof ScheduledReportFormValues>(
    key: Key,
    value: ScheduledReportFormValues[Key],
  ) => {
    setValues((current) => ({ ...current, [key]: value }))
    setFieldErrors((current) => ({ ...current, [key]: undefined }))
    setSubmitError(null)
  }

  const handleSubmit = async () => {
    const result = scheduledReportFormSchema.safeParse(values)
    if (!result.success) {
      const nextErrors: FieldErrors = {}
      for (const issue of result.error.issues) {
        const field = issue.path[0]
        if (typeof field === 'string' && !(field in nextErrors)) {
          nextErrors[field as keyof ScheduledReportFormValues] = issue.message
        }
      }
      setFieldErrors(nextErrors)
      setSubmitError(null)
      return
    }

    const payload = toScheduledReportRequest(result.data)
    setFieldErrors({})
    setSubmitError(null)
    try {
      if (report) {
        await updateMutation.mutateAsync({ id: report.id, data: payload })
        toast.success('Scheduled report updated.')
      } else {
        await createMutation.mutateAsync(payload)
        toast.success('Scheduled report created.')
      }
      onOpenChange(false)
    } catch (error) {
      setSubmitError(
        error instanceof ApiError ? error.message : 'Could not save the scheduled report.',
      )
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? 'Edit scheduled report' : 'Create scheduled report'}
          </DialogTitle>
          <DialogDescription>
            Send a recurring digest of uptime, errors, metrics and recent incidents to email or
            a Slack channel. Times use the report time zone shown below.
          </DialogDescription>
        </DialogHeader>

        <form
          className="mt-6 space-y-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void handleSubmit()
          }}
        >
          <FormField
            label="Name"
            icon={FileText}
            autoFocus
            autoComplete="off"
            placeholder="Weekly operations digest"
            maxLength={SCHEDULED_REPORT_NAME_MAX_LENGTH}
            value={values.name}
            error={fieldErrors.name}
            onChange={(event) => update('name', event.target.value)}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="report-frequency">Frequency</Label>
              <Select
                value={values.frequency}
                onValueChange={(value) =>
                  update('frequency', value as ScheduledReportFormValues['frequency'])
                }
              >
                <SelectTrigger id="report-frequency">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(['DAILY', 'WEEKLY', 'MONTHLY'] as const).map((frequency) => (
                    <SelectItem key={frequency} value={frequency}>
                      {scheduledReportFrequencyLabel(frequency)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="report-time">Time</Label>
              <Input
                id="report-time"
                type="time"
                value={values.timeOfDay}
                aria-invalid={Boolean(fieldErrors.timeOfDay)}
                onChange={(event) => update('timeOfDay', event.target.value)}
              />
              {fieldErrors.timeOfDay ? (
                <p className="text-xs text-red-400">{fieldErrors.timeOfDay}</p>
              ) : (
                <p className="text-xs text-slate-500">
                  {report?.timeZone ?? 'UTC'} time zone
                </p>
              )}
            </div>
          </div>

          {values.frequency === 'WEEKLY' ? (
            <div className="space-y-1.5">
              <Label htmlFor="report-day">Day of week</Label>
              <Select
                value={String(values.dayOfWeek ?? DEFAULT_SCHEDULED_REPORT_DAY)}
                onValueChange={(value) => update('dayOfWeek', Number(value))}
              >
                <SelectTrigger id="report-day">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {WEEKDAYS.map((day) => (
                    <SelectItem key={day} value={String(day)}>
                      {dayOfWeekLabel(day)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {fieldErrors.dayOfWeek ? (
                <p className="text-xs text-red-400">{fieldErrors.dayOfWeek}</p>
              ) : null}
            </div>
          ) : null}

          {values.frequency === 'MONTHLY' ? (
            <p className="rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2 text-xs text-slate-400">
              Monthly reports run on the first day of each month.
            </p>
          ) : null}

          <div className="space-y-1.5">
            <Label htmlFor="report-channel-type">Destination</Label>
            <Select
              value={values.channelType}
              onValueChange={(value) => {
                update('channelType', value as ScheduledReportFormValues['channelType'])
                update('channelId', null)
              }}
            >
              <SelectTrigger id="report-channel-type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="EMAIL">Email</SelectItem>
                <SelectItem value="SLACK">Slack</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {values.channelType === 'EMAIL' ? (
            <div className="space-y-1.5">
              <Label htmlFor="report-recipients">Recipients</Label>
              <textarea
                id="report-recipients"
                rows={3}
                value={values.recipients}
                aria-invalid={Boolean(fieldErrors.recipients)}
                aria-describedby="report-recipients-hint"
                placeholder="oncall@example.com, platform@example.com"
                className="flex w-full rounded-md border border-white/10 bg-canvas-2 px-3 py-2 text-sm text-slate-100 transition-colors placeholder:text-slate-500 focus-visible:border-cyan-400/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50 aria-[invalid=true]:border-red-500/60 aria-[invalid=true]:focus-visible:ring-red-500/40"
                onChange={(event) => update('recipients', event.target.value)}
              />
              {fieldErrors.recipients ? (
                <p className="text-xs text-red-400">{fieldErrors.recipients}</p>
              ) : (
                <p id="report-recipients-hint" className="text-xs text-slate-500">
                  Separate addresses with commas or new lines. Up to 10 recipients.
                </p>
              )}
            </div>
          ) : (
            <div className="space-y-1.5">
              <Label htmlFor="report-slack-channel">Slack channel</Label>
              {channelsQuery.isError ? (
                <p className="text-xs text-red-400">Could not load notification channels.</p>
              ) : slackChannels.length === 0 && !channelsQuery.isLoading ? (
                <p className="rounded-lg border border-amber-400/20 bg-amber-400/5 px-3 py-2 text-xs text-amber-100">
                  No active Slack channels. Add one on the{' '}
                  <Link to="/app/alerts" className="underline">
                    Alerts
                  </Link>{' '}
                  page first.
                </p>
              ) : (
                <Select
                  value={values.channelId === null ? '' : String(values.channelId)}
                  onValueChange={(value) => update('channelId', Number(value))}
                  disabled={channelsQuery.isLoading}
                >
                  <SelectTrigger id="report-slack-channel">
                    <SelectValue
                      placeholder={channelsQuery.isLoading ? 'Loading channels...' : 'Choose a channel'}
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {slackChannels.map((channel) => (
                      <SelectItem key={channel.id} value={String(channel.id)}>
                        Slack channel #{channel.id}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {fieldErrors.channelId ? (
                <p className="text-xs text-red-400">{fieldErrors.channelId}</p>
              ) : (
                <p className="text-xs text-slate-500">
                  Only active Slack webhook channels owned by this organization can be used.
                </p>
              )}
            </div>
          )}

          <fieldset className="space-y-3 rounded-xl border border-white/10 bg-white/[0.02] p-4">
            <legend className="px-1 text-sm font-medium text-slate-200">Sections</legend>
            <div className="flex items-center justify-between gap-4">
              <div>
                <Label htmlFor="report-uptime">Uptime</Label>
                <p className="text-xs text-slate-500">Health-check success rate for the period.</p>
              </div>
              <Switch
                id="report-uptime"
                checked={values.includeUptime}
                onCheckedChange={(checked) => update('includeUptime', checked)}
              />
            </div>
            <div className="flex items-center justify-between gap-4">
              <div>
                <Label htmlFor="report-errors">Top errors</Label>
                <p className="text-xs text-slate-500">Most frequent error log messages.</p>
              </div>
              <Switch
                id="report-errors"
                checked={values.includeTopErrors}
                onCheckedChange={(checked) => update('includeTopErrors', checked)}
              />
            </div>
            <div className="flex items-center justify-between gap-4">
              <div>
                <Label htmlFor="report-metrics">Metrics</Label>
                <p className="text-xs text-slate-500">Top metric series and sample counts.</p>
              </div>
              <Switch
                id="report-metrics"
                checked={values.includeMetrics}
                onCheckedChange={(checked) => update('includeMetrics', checked)}
              />
            </div>
            {fieldErrors.includeMetrics ? (
              <p className="text-xs text-red-400">{fieldErrors.includeMetrics}</p>
            ) : null}
          </fieldset>

          {submitError ? (
            <p role="alert" className="rounded-lg border border-red-500/30 bg-red-500/5 px-3 py-2 text-xs text-red-200">
              {submitError}
            </p>
          ) : null}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              <CalendarClock className="h-4 w-4" aria-hidden="true" />
              {isSubmitting ? 'Saving...' : isEditing ? 'Save changes' : 'Create report'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
