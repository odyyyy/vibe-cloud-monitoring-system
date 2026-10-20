import { useMemo } from 'react'
import { Pencil, Trash2 } from 'lucide-react'
import type { Widget, WidgetType } from '../../api/dashboards'
import { useDashboardVariableValues } from '../../features/dashboards/DashboardVariablesContext'
import { parseWidgetConfig } from '../../features/dashboards/schemas'
import { interpolateWidgetConfig } from '../../features/dashboards/variables'
import { metricColor } from '../../features/metrics/catalog'
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
  showActions: boolean
  refetchInterval: number
  onEdit: (widget: Widget) => void
  onDelete: (widget: Widget) => void
  /** Share token of a public dashboard; the data is then read through the public endpoint. */
  shareToken?: string
}

export function DashboardWidget({ widget, showActions, refetchInterval, onEdit, onDelete, shareToken }: DashboardWidgetProps) {
  const variableValues = useDashboardVariableValues()
  const parsed = useMemo(() => parseWidgetConfig(widget.type, widget.config), [widget.type, widget.config])
  // Resolve `${var}` placeholders before the widget runs its query; without a variables
  // provider (e.g. the overview page) the values map is empty and the config passes through.
  const resolved = useMemo(
    () => (parsed.success ? interpolateWidgetConfig(parsed.data, variableValues) : null),
    [parsed, variableValues],
  )

  const title = resolved
    ? resolved.type === 'METRIC_CHART'
      ? resolved.config.metricName
      : resolved.type === 'LOG_TABLE'
        ? 'Logs'
        : 'Status'
    : TYPE_LABELS[widget.type]

  const accent = resolved && resolved.type === 'METRIC_CHART' ? metricColor(resolved.config.metricName) : null

  return (
    <article className="flex h-full flex-col overflow-hidden rounded-2xl border border-white/10 bg-panel/60">
      <header className="flex items-center gap-2 border-b border-white/5 px-3 py-2">
        <span className="widget-drag-handle flex min-w-0 flex-1 cursor-grab items-center gap-2 active:cursor-grabbing">
          <Badge variant="muted" className="shrink-0">
            {TYPE_LABELS[widget.type]}
          </Badge>
          {accent ? (
            <span aria-hidden="true" className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: accent }} />
          ) : null}
          <h3 className="min-w-0 flex-1 truncate text-sm font-medium text-slate-200">{title}</h3>
        </span>
        {showActions ? (
          <div
            className="flex shrink-0 items-center gap-1"
            onMouseDown={(event) => event.stopPropagation()}
            onTouchStart={(event) => event.stopPropagation()}
          >
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
          ) : resolved === null ? null : resolved.type === 'METRIC_CHART' ? (
            <MetricChartWidget
              widgetId={widget.id}
              config={resolved.config}
              refetchInterval={refetchInterval}
              shareToken={shareToken}
            />
          ) : resolved.type === 'LOG_TABLE' ? (
            <LogTableWidget
              widgetId={widget.id}
              config={resolved.config}
              refetchInterval={refetchInterval}
              shareToken={shareToken}
            />
          ) : (
            <StatusWidget applicationId={resolved.config.applicationId} />
          )}
        </WidgetErrorBoundary>
      </div>
    </article>
  )
}
