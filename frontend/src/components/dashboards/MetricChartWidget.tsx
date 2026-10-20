import { MetricChart } from '../metrics/MetricChart'
import type { MetricChartConfig } from '../../features/dashboards/schemas'
import { useMetricChartWidget } from '../../features/dashboards/useWidgetData'
import { metricColor } from '../../features/metrics/catalog'

interface MetricChartWidgetProps {
  widgetId: number
  config: MetricChartConfig
  refetchInterval: number
  /** Share token of a public dashboard; the data is then read through the public endpoint. */
  shareToken?: string
}

export function MetricChartWidget({ widgetId, config, refetchInterval, shareToken }: MetricChartWidgetProps) {
  const query = useMetricChartWidget(widgetId, config, refetchInterval, shareToken)
  const error = query.error instanceof Error ? query.error : null

  return (
    <MetricChart
      bare
      heightClass="h-full"
      title={config.metricName}
      accentColor={metricColor(config.metricName)}
      data={query.data ?? []}
      isLoading={query.isLoading}
      error={error}
    />
  )
}
