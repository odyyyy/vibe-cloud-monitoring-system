import { describe, expect, it } from 'vitest'
import type { Layout } from 'react-grid-layout'
import type { Widget } from '../../api/dashboards'
import { MIN_WIDGET_HEIGHT, MIN_WIDGET_WIDTH } from './schemas'
import { resolveGridLayout, widgetToLayout } from './layout'

function item(i: string, x: number, y: number, w: number, h: number): Layout {
  return { i, x, y, w, h }
}

function widget(overrides: Partial<Widget>): Widget {
  return {
    id: 1,
    dashboardId: 1,
    type: 'METRIC_CHART',
    config: '{}',
    positionX: 0,
    positionY: 0,
    width: 12,
    height: 10,
    ...overrides,
  }
}

describe('widgetToLayout', () => {
  it('keeps a widget that is already large enough', () => {
    const result = widgetToLayout(widget({ id: 7, positionX: 1, positionY: 2, width: 12, height: 10 }))

    expect(result).toMatchObject({ i: '7', x: 1, y: 2, w: 12, h: 10 })
  })

  it('expands a widget stored too small up to the minimum size', () => {
    const result = widgetToLayout(widget({ width: 1, height: 1 }))

    expect(result.w).toBe(MIN_WIDGET_WIDTH)
    expect(result.h).toBe(MIN_WIDGET_HEIGHT)
  })

  it('exposes the minimum size as resize bounds', () => {
    const result = widgetToLayout(widget({}))

    expect(result.minW).toBe(MIN_WIDGET_WIDTH)
    expect(result.minH).toBe(MIN_WIDGET_HEIGHT)
  })
})

describe('resolveGridLayout', () => {
  it('returns the server layout when there is no local state', () => {
    const server = [item('1', 0, 0, 6, 4), item('2', 6, 0, 6, 4)]
    expect(resolveGridLayout(null, server)).toBe(server)
  })

  it('keeps local positions for known widgets', () => {
    const local = [item('1', 3, 2, 8, 5)]
    const server = [item('1', 0, 0, 6, 4), item('2', 6, 0, 6, 4)]

    const result = resolveGridLayout(local, server)

    expect(result).toEqual([item('1', 3, 2, 8, 5), item('2', 6, 0, 6, 4)])
  })

  it('includes widgets the local state does not know about yet', () => {
    const local = [item('1', 0, 0, 6, 4)]
    const server = [item('1', 0, 0, 6, 4), item('2', 6, 0, 6, 4)]

    const result = resolveGridLayout(local, server)

    expect(result.map((entry) => entry.i)).toEqual(['1', '2'])
    expect(result[1]).toEqual(item('2', 6, 0, 6, 4))
  })

  it('never returns fewer items than the server has widgets', () => {
    const server = [item('1', 0, 0, 6, 4), item('2', 6, 0, 6, 4), item('3', 0, 4, 6, 4)]

    const result = resolveGridLayout([], server)

    expect(result).toHaveLength(server.length)
    for (const entry of result) {
      expect(entry.w).toBeGreaterThan(1)
      expect(entry.h).toBeGreaterThan(1)
    }
  })
})
