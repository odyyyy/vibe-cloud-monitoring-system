import { useMemo } from 'react'
import { Pencil, Trash2 } from 'lucide-react'
import type { Widget, WidgetType } from '../../api/dashboards'
import { parseWidgetConfig } from '../../features/dashboards/schemas'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { LogTableWidget } from './LogTableWidget'
import { MetricChartWidget } from './MetricChartWidget'
import { StatusWidget } from './StatusWidget'
import { WidgetErrorBoundary } from './WidgetErrorBoundary'

const TYPE_LABELS: Record<WidgetType, string> = {
  METRIC_CHART: 'Chart',
  LOG_TABLE: 'Logs',
  STATUS: 'Status',
}

interface DashboardWidgetProps {
  widget: Widget
  editable: boolean
  refetchInterval: number
  onEdit: (widget: Widget) => void
  onDelete: (widget: Widget) => void
}

export function DashboardWidget({ widget, editable, refetchInterval, onEdit, onDelete }: DashboardWidgetProps) {
  const parsed = useMemo(() => parseWidgetConfig(widget.type, widget.config), [widget.type, widget.config])

  const title = parsed.success
    ? parsed.data.type === 'METRIC_CHART'
      ? parsed.data.config.metricName
      : parsed.data.type === 'LOG_TABLE'
        ? 'Logs'
        : 'Status'
    : TYPE_LABELS[widget.type]

  return (
    <article className="flex h-full flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#0b101b]/60">
      <header className="widget-drag-handle flex items-center gap-2 border-b border-white/5 px-3 py-2">
        <Badge variant="muted" className="shrink-0">
          {TYPE_LABELS[widget.type]}
        </Badge>
        <h3 className="min-w-0 flex-1 truncate text-sm font-medium text-slate-200">{title}</h3>
        {editable ? (
          <div className="flex shrink-0 items-center gap-1">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              aria-label="Edit widget"
              onClick={() => onEdit(widget)}
            >
              <Pencil className="h-3.5 w-3.5" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-red-300 hover:text-red-200"
              aria-label="Delete widget"
              onClick={() => onDelete(widget)}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        ) : null}
      </header>

      <div className="min-h-0 flex-1 p-2">
        <WidgetErrorBoundary>
          {!parsed.success ? (
            <div role="alert" className="flex h-full items-center justify-center rounded-xl border border-red-500/20 bg-red-500/5 p-4">
              <p className="text-center text-sm text-red-300">{parsed.error}</p>
            </div>
          ) : parsed.data.type === 'METRIC_CHART' ? (
            <MetricChartWidget widgetId={widget.id} config={parsed.data.config} refetchInterval={refetchInterval} />
          ) : parsed.data.type === 'LOG_TABLE' ? (
            <LogTableWidget widgetId={widget.id} config={parsed.data.config} refetchInterval={refetchInterval} />
          ) : (
            <StatusWidget applicationId={parsed.data.config.applicationId} />
          )}
        </WidgetErrorBoundary>
      </div>
    </article>
  )
}
