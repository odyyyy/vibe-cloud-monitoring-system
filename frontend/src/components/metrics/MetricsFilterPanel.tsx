import type { Aggregation } from '../../api/metrics'
import type { ExportFormat } from '../../api/client'
import type { MetricFilters } from '../../features/metrics/searchParams'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { MetricNameSelect } from '../filters/MetricNameSelect'
import { TimeRangePicker } from '../filters/TimeRangePicker'
import { ExportMenu } from '../ExportMenu'
import { Button } from '../ui/button'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

const AGGREGATIONS: Aggregation[] = ['AVG', 'MAX', 'MIN', 'SUM']
const INTERVALS = [1, 5, 15, 60, 1440]

interface MetricsFilterPanelProps {
  draft: MetricFilters
  onChange: (patch: Partial<MetricFilters>) => void
  onApply: () => void
  onExport: (format: ExportFormat) => void
  error: string | null
  isApplying: boolean
  isExporting: boolean
  canExport: boolean
}

export function MetricsFilterPanel({
  draft,
  onChange,
  onApply,
  onExport,
  error,
  isApplying,
  isExporting,
  canExport,
}: MetricsFilterPanelProps) {
  return (
    <form
      className="rounded-2xl border border-white/10 bg-panel/60 p-4 sm:p-5"
      onSubmit={(event) => {
        event.preventDefault()
        onApply()
      }}
    >
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="metrics-application">Application</Label>
          <ApplicationSelect
            id="metrics-application"
            value={draft.applicationId}
            onChange={(value) => onChange({ applicationId: value, metricName: '' })}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="metrics-metric-name">Metric name</Label>
          <MetricNameSelect
            id="metrics-metric-name"
            applicationId={draft.applicationId}
            value={draft.metricName}
            onChange={(metricName) => onChange({ metricName })}
          />
        </div>

        <div className="space-y-1.5">
          <Label>Time range</Label>
          <TimeRangePicker value={draft.range} onChange={(range) => onChange({ range })} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="metrics-aggregation">Aggregation</Label>
          <Select value={draft.aggregation} onValueChange={(value) => onChange({ aggregation: value as Aggregation })}>
            <SelectTrigger id="metrics-aggregation">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {AGGREGATIONS.map((aggregation) => (
                <SelectItem key={aggregation} value={aggregation}>
                  {aggregation}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="metrics-interval">Interval</Label>
          <Select
            value={String(draft.intervalMinutes)}
            onValueChange={(value) => onChange({ intervalMinutes: Number(value) })}
          >
            <SelectTrigger id="metrics-interval">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {INTERVALS.map((interval) => (
                <SelectItem key={interval} value={String(interval)}>
                  {interval} min
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex items-end gap-2">
          <Button type="submit" className="flex-1" disabled={isApplying}>
            Apply
          </Button>
          <ExportMenu onExport={onExport} disabled={!canExport} isExporting={isExporting} />
        </div>
      </div>

      {error ? (
        <p role="alert" className="mt-3 text-sm text-red-400">
          {error}
        </p>
      ) : null}
    </form>
  )
}
