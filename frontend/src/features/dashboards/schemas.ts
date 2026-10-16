import { z } from 'zod'
import type { WidgetType } from '../../api/dashboards'

export const WIDGET_TYPES = ['METRIC_CHART', 'LOG_TABLE', 'STATUS'] as const
export const widgetTypeSchema = z.enum(WIDGET_TYPES)

export const AGGREGATIONS = ['AVG', 'MAX', 'MIN', 'SUM'] as const
export const aggregationSchema = z.enum(AGGREGATIONS)

export const LOG_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR'] as const
export const logLevelSchema = z.enum(LOG_LEVELS)

export const TIME_RANGE_HOURS_OPTIONS = [
  { value: 1, label: 'Last 1h' },
  { value: 6, label: 'Last 6h' },
  { value: 24, label: 'Last 24h' },
  { value: 168, label: 'Last 7d' },
] as const

export const DEFAULT_TIME_RANGE_HOURS = 1
export const MAX_TIME_RANGE_HOURS = 24 * 30
export const MAX_SEARCH_TEXT_LENGTH = 200

export const MAX_WIDGETS_PER_DASHBOARD = 20
export const GRID_COLUMNS = 12
export const MAX_CONFIG_LENGTH = 8000
export const DEFAULT_WIDGET_WIDTH = 12
export const DEFAULT_WIDGET_HEIGHT = 10
export const MIN_WIDGET_WIDTH = 4
export const MIN_WIDGET_HEIGHT = 6

export const DASHBOARD_NAME_MAX_LENGTH = 100
export const dashboardNameSchema = z
  .string()
  .trim()
  .min(1, 'Name is required')
  .max(DASHBOARD_NAME_MAX_LENGTH, `Name must be at most ${DASHBOARD_NAME_MAX_LENGTH} characters`)

export const dashboardFormSchema = z.object({ name: dashboardNameSchema })
export type DashboardFormValues = z.infer<typeof dashboardFormSchema>

const applicationIdSchema = z
  .number({ invalid_type_error: 'Select an application' })
  .int()
  .positive('Select an application')

const timeRangeHoursSchema = z
  .number({ invalid_type_error: 'Select a time range' })
  .positive('Select a time range')
  .max(MAX_TIME_RANGE_HOURS, `Time range must be at most ${MAX_TIME_RANGE_HOURS} hours`)

export const metricChartConfigSchema = z.object({
  applicationId: applicationIdSchema,
  metricName: z.string().trim().min(1, 'Metric name is required').max(255, 'Metric name is too long'),
  aggregation: aggregationSchema,
  intervalMinutes: z
    .number({ invalid_type_error: 'Select an interval' })
    .int()
    .positive('Interval must be greater than 0'),
  timeRangeHours: timeRangeHoursSchema,
})

export const logTableConfigSchema = z.object({
  applicationId: applicationIdSchema,
  level: logLevelSchema.optional(),
  searchText: z
    .string()
    .trim()
    .max(MAX_SEARCH_TEXT_LENGTH, `Search text must be at most ${MAX_SEARCH_TEXT_LENGTH} characters`)
    .optional(),
  timeRangeHours: timeRangeHoursSchema,
})

export const statusConfigSchema = z.object({
  applicationId: applicationIdSchema,
  timeRangeHours: timeRangeHoursSchema.optional(),
})

export type MetricChartConfig = z.infer<typeof metricChartConfigSchema>
export type LogTableConfig = z.infer<typeof logTableConfigSchema>
export type StatusConfig = z.infer<typeof statusConfigSchema>

export type ParsedWidgetConfig =
  | { type: 'METRIC_CHART'; config: MetricChartConfig }
  | { type: 'LOG_TABLE'; config: LogTableConfig }
  | { type: 'STATUS'; config: StatusConfig }

export type WidgetConfigParseResult =
  | { success: true; data: ParsedWidgetConfig }
  | { success: false; error: string }

/**
 * Parses a widget `config` JSON string against the schema for its type. The config
 * is untrusted (it round-trips through persistence), so both JSON parsing and schema
 * validation failures are reported instead of thrown.
 */
export function parseWidgetConfig(type: WidgetType, raw: string): WidgetConfigParseResult {
  let json: unknown
  try {
    json = JSON.parse(raw)
  } catch {
    return { success: false, error: 'Widget config is not valid JSON.' }
  }

  if (type === 'METRIC_CHART') {
    const result = metricChartConfigSchema.safeParse(json)
    return result.success
      ? { success: true, data: { type, config: result.data } }
      : { success: false, error: result.error.issues[0]?.message ?? 'Invalid chart config.' }
  }

  if (type === 'LOG_TABLE') {
    const result = logTableConfigSchema.safeParse(json)
    return result.success
      ? { success: true, data: { type, config: result.data } }
      : { success: false, error: result.error.issues[0]?.message ?? 'Invalid log table config.' }
  }

  if (type === 'STATUS') {
    const result = statusConfigSchema.safeParse(json)
    return result.success
      ? { success: true, data: { type, config: result.data } }
      : { success: false, error: result.error.issues[0]?.message ?? 'Invalid status config.' }
  }

  return { success: false, error: 'Unknown widget type.' }
}

export function serializeWidgetConfig(config: ParsedWidgetConfig['config']): string {
  return JSON.stringify(config)
}
