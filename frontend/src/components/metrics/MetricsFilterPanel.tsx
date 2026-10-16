import type { Aggregation } from '../../api/metrics'
import type { MetricFilters } from '../../features/metrics/searchParams'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { TimeRangePicker } from '../filters/TimeRangePicker'
import { Button } from '../ui/button'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

const AGGREGATIONS: Aggregation[] = ['AVG', 'MAX', 'MIN', 'SUM']
const INTERVALS = [1, 5, 15, 60, 1440]

interface MetricsFilterPanelProps {
  draft: MetricFilters
  onChange: (patch: Partial<MetricFilters>) => void
  onApply: () => void
  error: string | null
  isApplying: boolean
}

export function MetricsFilterPanel({ draft, onChange, onApply, error, isApplying }: MetricsFilterPanelProps) {
  return (
    <form
      className="rounded-2xl border border-white/10 bg-[#0b101b]/60 p-4 sm:p-5"
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
            onChange={(value) => onChange({ applicationId: value })}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="metrics-metric-name">Metric name</Label>
          <Input
            id="metrics-metric-name"
            placeholder="cpu_usage"
            autoComplete="off"
            value={draft.metricName}
            onChange={(event) => onChange({ metricName: event.target.value })}
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

        <div className="flex items-end">
          <Button type="submit" className="w-full" disabled={isApplying}>
            Apply
          </Button>
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
