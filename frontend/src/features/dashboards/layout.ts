import type { Layout } from 'react-grid-layout'
import type { Widget } from '../../api/dashboards'
import { MIN_WIDGET_HEIGHT, MIN_WIDGET_WIDTH } from './schemas'

/** Converts a persisted widget into a react-grid-layout item. */
export function widgetToLayout(widget: Widget): Layout {
  return {
    i: String(widget.id),
    x: widget.positionX,
    y: widget.positionY,
    w: widget.width,
    h: widget.height,
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
