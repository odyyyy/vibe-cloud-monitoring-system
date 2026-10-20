import { useEffect, useState } from 'react'
import type { Widget, WidgetType } from '../../api/dashboards'
import {
  AGGREGATIONS,
  DEFAULT_TIME_RANGE_HOURS,
  LOG_LEVELS,
  MAX_CONFIG_LENGTH,
  TIME_RANGE_HOURS_OPTIONS,
  WIDGET_TYPES,
  gaugeConfigSchema,
  logTableConfigSchema,
  metricChartConfigSchema,
  parseWidgetConfig,
  statusConfigSchema,
} from '../../features/dashboards/schemas'
import type { Aggregation } from '../../api/metrics'
import type { LogLevel } from '../../api/logs'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { MetricNameSelect } from '../filters/MetricNameSelect'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

const INTERVALS = [1, 5, 15, 60, 1440]
const WIDGET_TYPE_LABELS: Record<WidgetType, string> = {
  METRIC_CHART: 'Metric chart',
  GAUGE: 'Gauge',
  LOG_TABLE: 'Log table',
  STATUS: 'Status',
}

type LevelFilter = LogLevel | 'ALL'

export interface WidgetFormSubmit {
  type: WidgetType
  config: string
}

interface WidgetFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  widget: Widget | null
  onSubmit: (values: WidgetFormSubmit) => Promise<void>
}

export function WidgetFormDialog({ open, onOpenChange, widget, onSubmit }: WidgetFormDialogProps) {
  const [type, setType] = useState<WidgetType>('METRIC_CHART')
  const [applicationId, setApplicationId] = useState<number | null>(null)
  const [metricName, setMetricName] = useState('')
  const [aggregation, setAggregation] = useState<Aggregation>('AVG')
  const [intervalMinutes, setIntervalMinutes] = useState(5)
  const [groupByTag, setGroupByTag] = useState('')
  const [level, setLevel] = useState<LevelFilter>('ALL')
  const [searchText, setSearchText] = useState('')
  const [timeRangeHours, setTimeRangeHours] = useState<number>(DEFAULT_TIME_RANGE_HOURS)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    setError(null)
    setIsSubmitting(false)
    setType(widget?.type ?? 'METRIC_CHART')
    setApplicationId(null)
    setMetricName('')
    setAggregation('AVG')
    setIntervalMinutes(5)
    setGroupByTag('')
    setLevel('ALL')
    setSearchText('')
    setTimeRangeHours(DEFAULT_TIME_RANGE_HOURS)

    if (widget) {
      const parsed = parseWidgetConfig(widget.type, widget.config)
      if (parsed.success) {
        if (parsed.data.type === 'METRIC_CHART') {
          setApplicationId(parsed.data.config.applicationId)
          setMetricName(parsed.data.config.metricName)
          setAggregation(parsed.data.config.aggregation)
          setIntervalMinutes(parsed.data.config.intervalMinutes)
          setGroupByTag(parsed.data.config.groupByTag ?? '')
          setTimeRangeHours(parsed.data.config.timeRangeHours)
        } else if (parsed.data.type === 'GAUGE') {
          setApplicationId(parsed.data.config.applicationId)
          setMetricName(parsed.data.config.metricName)
          setTimeRangeHours(parsed.data.config.timeRangeHours)
        } else if (parsed.data.type === 'LOG_TABLE') {
          setApplicationId(parsed.data.config.applicationId)
          setLevel(parsed.data.config.level ?? 'ALL')
          setSearchText(parsed.data.config.searchText ?? '')
          setTimeRangeHours(parsed.data.config.timeRangeHours)
        } else {
          setApplicationId(parsed.data.config.applicationId)
          if (parsed.data.config.timeRangeHours) {
            setTimeRangeHours(parsed.data.config.timeRangeHours)
          }
        }
      }
    }
  }, [open, widget])

  const handleSubmit = async () => {
    const candidate =
      type === 'METRIC_CHART'
        ? {
            applicationId: applicationId ?? undefined,
            metricName: metricName.trim(),
            aggregation,
            intervalMinutes,
            groupByTag: groupByTag.trim() === '' ? undefined : groupByTag.trim(),
            timeRangeHours,
          }
        : type === 'GAUGE'
          ? {
              applicationId: applicationId ?? undefined,
              metricName: metricName.trim(),
              timeRangeHours,
            }
          : type === 'LOG_TABLE'
          ? {
              applicationId: applicationId ?? undefined,
              level: level === 'ALL' ? undefined : level,
              searchText: searchText.trim() === '' ? undefined : searchText,
              timeRangeHours,
            }
          : { applicationId: applicationId ?? undefined, timeRangeHours }

    const schema =
      type === 'METRIC_CHART'
        ? metricChartConfigSchema
        : type === 'GAUGE'
          ? gaugeConfigSchema
          : type === 'LOG_TABLE'
            ? logTableConfigSchema
            : statusConfigSchema
    const result = schema.safeParse(candidate)

    if (!result.success) {
      setError(result.error.issues[0]?.message ?? 'Invalid widget configuration.')
      return
    }

    const config = JSON.stringify(result.data)
    if (config.length > MAX_CONFIG_LENGTH) {
      setError('Widget configuration is too large.')
      return
    }

    setError(null)
    setIsSubmitting(true)
    try {
      await onSubmit({ type, config })
      onOpenChange(false)
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Could not save the widget.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{widget ? 'Edit widget' : 'Add widget'}</DialogTitle>
          <DialogDescription>Configure the data this widget displays.</DialogDescription>
        </DialogHeader>

        <form
          className="mt-6 space-y-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void handleSubmit()
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="widget-type">Widget type</Label>
            <Select value={type} onValueChange={(value) => setType(value as WidgetType)}>
              <SelectTrigger id="widget-type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {WIDGET_TYPES.map((widgetType) => (
                  <SelectItem key={widgetType} value={widgetType}>
                    {WIDGET_TYPE_LABELS[widgetType]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="widget-application">Application</Label>
            <ApplicationSelect
              id="widget-application"
              value={applicationId}
              onChange={(next) => {
                setApplicationId(next)
                setMetricName('')
              }}
            />
          </div>

          {type === 'METRIC_CHART' || type === 'GAUGE' ? (
            <div className="space-y-1.5">
              <Label htmlFor="widget-metric">Metric name</Label>
              <MetricNameSelect
                id="widget-metric"
                applicationId={applicationId}
                value={metricName}
                onChange={setMetricName}
              />
            </div>
          ) : null}

          {type === 'METRIC_CHART' ? (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="widget-aggregation">Aggregation</Label>
                  <Select value={aggregation} onValueChange={(value) => setAggregation(value as Aggregation)}>
                    <SelectTrigger id="widget-aggregation">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {AGGREGATIONS.map((value) => (
                        <SelectItem key={value} value={value}>
                          {value}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="widget-interval">Interval</Label>
                  <Select value={String(intervalMinutes)} onValueChange={(value) => setIntervalMinutes(Number(value))}>
                    <SelectTrigger id="widget-interval">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {INTERVALS.map((value) => (
                        <SelectItem key={value} value={String(value)}>
                          {value} min
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="widget-group">Group by tag (optional)</Label>
                <Input
                  id="widget-group"
                  placeholder="e.g. status"
                  autoComplete="off"
                  value={groupByTag}
                  onChange={(event) => setGroupByTag(event.target.value)}
                />
                <p className="text-xs text-slate-500">Renders one line per tag value.</p>
              </div>
            </>
          ) : null}

          {type === 'LOG_TABLE' ? (
            <>
              <div className="space-y-1.5">
                <Label htmlFor="widget-level">Level</Label>
                <Select value={level} onValueChange={(value) => setLevel(value as LevelFilter)}>
                  <SelectTrigger id="widget-level">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">All levels</SelectItem>
                    {LOG_LEVELS.map((value) => (
                      <SelectItem key={value} value={value}>
                        {value}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="widget-search">Search text</Label>
                <Input
                  id="widget-search"
                  placeholder="Search messages…"
                  autoComplete="off"
                  value={searchText}
                  onChange={(event) => setSearchText(event.target.value)}
                />
              </div>
            </>
          ) : null}

          {type === 'STATUS' ? (
            <p className="rounded-lg border border-white/10 bg-white/[0.02] p-3 text-xs text-slate-400">
              The status widget is a placeholder. Service health is not available yet.
            </p>
          ) : null}

          <div className="space-y-1.5">
            <Label htmlFor="widget-range">Time range</Label>
            <Select value={String(timeRangeHours)} onValueChange={(value) => setTimeRangeHours(Number(value))}>
              <SelectTrigger id="widget-range">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TIME_RANGE_HOURS_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={String(option.value)}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {error ? (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : widget ? 'Save widget' : 'Add widget'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
