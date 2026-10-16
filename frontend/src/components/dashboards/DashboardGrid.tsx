import type { ReactNode } from 'react'
import { Responsive, WidthProvider, type Layout } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

const ResponsiveGridLayout = WidthProvider(Responsive)

export interface DashboardGridItem {
  id: number
  node: ReactNode
}

interface DashboardGridProps {
  items: DashboardGridItem[]
  layout: Layout[]
  editable: boolean
  onLayoutChange: (layout: Layout[]) => void
}

export function DashboardGrid({ items, layout, editable, onLayoutChange }: DashboardGridProps) {
  return (
    <ResponsiveGridLayout
      layouts={{ lg: layout, md: layout, sm: layout, xs: layout, xxs: layout }}
      breakpoints={{ lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 }}
      cols={{ lg: 12, md: 12, sm: 12, xs: 12, xxs: 12 }}
      rowHeight={30}
      margin={[16, 16]}
      isDraggable={editable}
      isResizable={editable}
      draggableHandle=".widget-drag-handle"
      compactType="vertical"
      resizeHandles={['se']}
      onLayoutChange={(current, all) => onLayoutChange((all.lg as Layout[] | undefined) ?? (current as Layout[]))}
    >
      {items.map((item) => (
        <div key={String(item.id)}>{item.node}</div>
      ))}
    </ResponsiveGridLayout>
  )
}
