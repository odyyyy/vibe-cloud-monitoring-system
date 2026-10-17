/**
 * Catalog of metric names the platform understands out of the box, plus the accent colors used
 * to draw each metric.
 *
 * <p>Colors come from the product palette defined in {@code index.css} (`--cyan`, `--violet`,
 * `--lime`, `--amber` and the light variants used by the text gradient), so charts stay
 * consistent with the rest of the interface.
 */

/** Accent colors taken from the design palette, in display order. */
export const METRIC_PALETTE = ['#22d3ee', '#8b5cf6', '#a3e635', '#fbbf24', '#67e8f9', '#a78bfa'] as const

export interface MetricDefinition {
  name: string
  label: string
  color: string
}

/**
 * Metrics collected by the Go agent by default. They are offered in the picker even before
 * the application has reported any data, so a metric can be selected on a fresh workspace.
 */
export const BUILT_IN_METRICS: readonly MetricDefinition[] = [
  { name: 'system.cpu.usage', label: 'CPU usage', color: METRIC_PALETTE[0] },
  { name: 'system.memory.usage', label: 'Memory usage', color: METRIC_PALETTE[1] },
  { name: 'system.disk.usage', label: 'Disk usage', color: METRIC_PALETTE[2] },
]

const BUILT_IN_BY_NAME: ReadonlyMap<string, MetricDefinition> = new Map(
  BUILT_IN_METRICS.map((metric) => [metric.name, metric]),
)

/** Friendly label for a metric name; falls back to the raw name for custom metrics. */
export function metricLabel(name: string): string {
  return BUILT_IN_BY_NAME.get(name)?.label ?? name
}

/**
 * Accent color for a metric. Built-in metrics use a fixed color; any other name is mapped
 * deterministically onto the palette so the same metric keeps the same color across reloads
 * and across widgets.
 */
export function metricColor(name: string): string {
  const builtIn = BUILT_IN_BY_NAME.get(name)
  if (builtIn) {
    return builtIn.color
  }

  let hash = 0
  for (let index = 0; index < name.length; index += 1) {
    hash = (hash * 31 + name.charCodeAt(index)) >>> 0
  }
  return METRIC_PALETTE[hash % METRIC_PALETTE.length]
}
