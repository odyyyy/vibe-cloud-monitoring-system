import type { DragEvent } from 'react'
import { Link } from 'react-router-dom'
import { ArrowDown, ArrowUp, ExternalLink, GripVertical, LayoutGrid, Lock, Share2 } from 'lucide-react'
import type { Dashboard } from '../../api/dashboards'
import { widgetToLayout } from '../../features/dashboards/layout'
import { cn } from '../../lib/utils'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { DashboardGrid, type DashboardGridItem } from './DashboardGrid'
import { DashboardWidget } from './DashboardWidget'

interface DashboardSectionProps {
  dashboard: Dashboard
  pollInterval: number
  index: number
  total: number
  isDragging: boolean
  onDragStart: () => void
  onDragEnd: () => void
  onDragOver: (event: DragEvent<HTMLElement>) => void
  onDrop: () => void
  onMoveUp: () => void
  onMoveDown: () => void
  onShareClick: (dashboard: Dashboard) => void
}

export function DashboardSection({
  dashboard,
  pollInterval,
  index,
  total,
  isDragging,
  onDragStart,
  onDragEnd,
  onDragOver,
  onDrop,
  onMoveUp,
  onMoveDown,
  onShareClick,
}: DashboardSectionProps) {
  const readOnly = dashboard.isSystem === true
  const items: DashboardGridItem[] = dashboard.widgets.map((widget) => ({
    id: widget.id,
    node: (
      <DashboardWidget
        widget={widget}
        showActions={false}
        refetchInterval={pollInterval}
        onEdit={noop}
        onDelete={noop}
      />
    ),
  }))

  return (
    <section
      aria-label={`Dashboard ${dashboard.name}`}
      onDragOver={onDragOver}
      onDrop={onDrop}
      className={cn(
        'space-y-4 rounded-2xl border border-white/10 bg-panel/30 p-4 sm:p-5',
        isDragging && 'border-cyan-400/40 opacity-60',
      )}
    >
      <header className="flex items-center gap-3">
        <span
          className={cn(
            'text-slate-500 transition-colors hover:text-slate-300',
            !readOnly && 'cursor-grab active:cursor-grabbing',
          )}
          draggable={!readOnly}
          onDragStart={onDragStart}
          onDragEnd={onDragEnd}
          title={readOnly ? 'System dashboard' : 'Drag to reorder'}
          aria-hidden="true"
        >
          {readOnly ? <Lock className="h-4 w-4" /> : <GripVertical className="h-4 w-4" />}
        </span>

        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-white/10 bg-white/[0.03]">
          <LayoutGrid className="h-4 w-4 text-cyan-300" aria-hidden="true" />
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h2 className="truncate font-display text-base font-semibold text-slate-100">{dashboard.name}</h2>
            {readOnly ? <Badge variant="muted">System</Badge> : null}
            <Badge variant="muted">
              {dashboard.widgets.length} {dashboard.widgets.length === 1 ? 'widget' : 'widgets'}
            </Badge>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1">
          {!readOnly ? (
            <>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                aria-label={`Move ${dashboard.name} up`}
                disabled={index === 0}
                onClick={onMoveUp}
              >
                <ArrowUp className="h-4 w-4" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                aria-label={`Move ${dashboard.name} down`}
                disabled={index === total - 1}
                onClick={onMoveDown}
              >
                <ArrowDown className="h-4 w-4" />
              </Button>
            </>
          ) : null}
          <Button asChild variant="ghost" size="icon" className="h-8 w-8" aria-label={`Open ${dashboard.name}`}>
            <Link to={`/app/dashboards/${dashboard.id}`}>
              <ExternalLink className="h-4 w-4" />
            </Link>
          </Button>
          {!readOnly ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              aria-label={`Share ${dashboard.name}`}
              onClick={() => onShareClick(dashboard)}
            >
              <Share2 className="h-4 w-4" />
            </Button>
          ) : null}
        </div>
      </header>

      {dashboard.widgets.length === 0 ? (
        <p className="rounded-xl border border-dashed border-white/10 bg-white/[0.02] p-6 text-center text-sm text-slate-400">
          This dashboard has no widgets yet.{' '}
          {!readOnly ? (
            <Link to={`/app/dashboards/${dashboard.id}`} className="text-cyan-300 hover:underline">
              Add widgets
            </Link>
          ) : null}
        </p>
      ) : (
        <DashboardGrid
          items={items}
          layout={dashboard.widgets.map(widgetToLayout)}
          interactive={false}
          onLayoutChange={noop}
        />
      )}
    </section>
  )
}

function noop() {
  return undefined
}
