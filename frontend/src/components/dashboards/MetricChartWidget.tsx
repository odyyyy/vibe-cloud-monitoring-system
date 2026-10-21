import { MetricChart } from '../metrics/MetricChart'
import type { MetricChartConfig } from '../../features/dashboards/schemas'
import { useMetricChartWidget } from '../../features/dashboards/useWidgetData'
import { metricColor, metricLabel } from '../../features/metrics/catalog'

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

  // Public (share-token) views have no session, so alert rules are only overlaid for
  // authenticated dashboard viewers.
  const canLoadThresholds = shareToken === undefined

  return (
    <MetricChart
      bare
      heightClass="h-full"
      title={metricLabel(config.metricName)}
      accentColor={metricColor(config.metricName)}
      data={query.data ?? []}
      isLoading={query.isLoading}
      error={error}
      applicationId={canLoadThresholds ? config.applicationId : null}
      metricName={canLoadThresholds ? config.metricName : undefined}
    />
  )
}
