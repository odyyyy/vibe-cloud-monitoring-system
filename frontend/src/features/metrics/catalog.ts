/**
 * Catalog of metric names the platform understands out of the box, plus the accent colors used
 * to draw each metric.
 *
 * <p>Colors come from the product palette defined in {@code index.css} (the accent,
 * status and chart scales), so charts stay consistent with the rest of the interface.
 */

/** Accent colors taken from the design palette, in display order. */
export const METRIC_PALETTE = [
  '#22d3ee', // cyan
  '#8b5cf6', // violet
  '#a3e635', // lime
  '#fbbf24', // amber
  '#67e8f9', // light cyan
  '#a78bfa', // light violet
  '#34d399', // emerald
  '#fb7185', // rose
  '#f87171', // red
  '#94a3b8', // slate
  '#38bdf8', // sky
  '#f472b6', // pink
  '#2dd4bf', // teal
] as const

export interface MetricDefinition {
  name: string
  label: string
  color: string
}

/**
 * Metrics collected by the Go agent by default. They are offered in the picker even before
 * the application has reported any data, so a metric can be selected on a fresh workspace.
 * Network metrics are reported per interface (the `interface` tag holds the interface
 * name); the agent sends absolute counters and the backend derives rates at query time.
 */
export const BUILT_IN_METRICS: readonly MetricDefinition[] = [
  { name: 'system.cpu.usage', label: 'CPU usage', color: METRIC_PALETTE[0] },
  { name: 'system.memory.usage', label: 'Memory usage', color: METRIC_PALETTE[1] },
  { name: 'system.disk.usage', label: 'Disk usage', color: METRIC_PALETTE[2] },
  { name: 'system.load.1', label: 'Load average (1 min)', color: METRIC_PALETTE[3] },
  { name: 'system.load.5', label: 'Load average (5 min)', color: METRIC_PALETTE[4] },
  { name: 'system.load.15', label: 'Load average (15 min)', color: METRIC_PALETTE[5] },
  { name: 'system.uptime', label: 'System uptime', color: METRIC_PALETTE[6] },
  { name: 'system.network.bytes_sent', label: 'Network bytes sent', color: METRIC_PALETTE[7] },
  { name: 'system.network.bytes_recv', label: 'Network bytes received', color: METRIC_PALETTE[8] },
  { name: 'system.network.packets_sent', label: 'Network packets sent', color: METRIC_PALETTE[9] },
  { name: 'system.network.packets_recv', label: 'Network packets received', color: METRIC_PALETTE[10] },
  { name: 'system.network.drop_in', label: 'Network drops inbound', color: METRIC_PALETTE[11] },
  { name: 'system.network.drop_out', label: 'Network drops outbound', color: METRIC_PALETTE[12] },
]

const BUILT_IN_BY_NAME: ReadonlyMap<string, MetricDefinition> = new Map(
  BUILT_IN_METRICS.map((metric) => [metric.name, metric]),
)

/** Platform self-monitoring metrics are not collected by agents, but get friendly titles. */
const PLATFORM_METRIC_LABELS: Readonly<Record<string, string>> = {
  jvm_memory_used_bytes: 'JVM Heap Usage',
  hikaricp_connections_active: 'Active DB Connections',
  tomcat_threads_busy: 'Tomcat Busy Threads',
  ingest_requests_total: 'Ingestion Request Rate',
  ingest_request_duration_seconds: 'Ingestion Latency (p95)',
}

/** Friendly label for a metric name; falls back to the raw name for custom metrics. */
export function metricLabel(name: string): string {
  return BUILT_IN_BY_NAME.get(name)?.label ?? PLATFORM_METRIC_LABELS[name] ?? name
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
