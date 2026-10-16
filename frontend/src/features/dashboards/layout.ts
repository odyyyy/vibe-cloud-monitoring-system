import type { Layout } from 'react-grid-layout'
import type { Widget } from '../../api/dashboards'
import { MIN_WIDGET_HEIGHT, MIN_WIDGET_WIDTH } from './schemas'

/**
 * Converts a persisted widget into a react-grid-layout item.
 *
 * Width and height are clamped up to the minimum so widgets stored too small
 * (which makes charts/tables overlap and become unreadable) render expanded.
 * The clamped size is persisted on the next layout change.
 */
export function widgetToLayout(widget: Widget): Layout {
  return {
    i: String(widget.id),
    x: widget.positionX,
    y: widget.positionY,
    w: Math.max(widget.width, MIN_WIDGET_WIDTH),
    h: Math.max(widget.height, MIN_WIDGET_HEIGHT),
    minW: MIN_WIDGET_WIDTH,
    minH: MIN_WIDGET_HEIGHT,
  }
}

/** Compares two layouts by widget id and geometry (order-independent). */
export function layoutsEqual(a: readonly Layout[], b: readonly Layout[]): boolean {
  if (a.length !== b.length) {
    return false
  }
  const byId = new Map(b.map((item) => [item.i, item]))
  return a.every((item) => {
    const other = byId.get(item.i)
    return (
      other !== undefined &&
      other.x === item.x &&
      other.y === item.y &&
      other.w === item.w &&
      other.h === item.h
    )
  })
}

/**
 * Builds the layout handed to react-grid-layout for the current widgets.
 *
 * Every persisted widget must have exactly one item: if a child has no entry,
 * react-grid-layout auto-generates a default 1x1 cell and overwrites the
 * persisted position. Local (possibly just-dragged) positions take precedence,
 * while widgets the local state does not know about yet fall back to the
 * server position.
 */
export function resolveGridLayout(local: Layout[] | null, server: Layout[]): Layout[] {
  if (local === null) {
    return server
  }
  const byId = new Map(local.map((item) => [item.i, item]))
  return server.map((item) => byId.get(item.i) ?? item)
}
