import type { DashboardVariable } from '../../api/dashboards'
import type { ParsedWidgetConfig } from './schemas'

export const VARIABLE_NAME_PATTERN = /^[A-Za-z0-9_]+$/
export const MAX_VARIABLES_PER_DASHBOARD = 20
export const MAX_VARIABLE_NAME_LENGTH = 64
export const MAX_VARIABLE_OPTIONS = 50
export const MAX_VARIABLE_OPTION_LENGTH = 200

/** Placeholder syntax: `${varName}`. Only alphanumeric names with underscores are interpolated. */
const PLACEHOLDER_PATTERN = /\$\{([A-Za-z0-9_]+)\}/g

export function isValidVariableName(name: string): boolean {
  return VARIABLE_NAME_PATTERN.test(name)
}

export function variablePlaceholder(name: string): string {
  return `\${${name}}`
}

/**
 * Builds the initial dropdown values from the persisted definitions. An empty default
 * falls back to the first option so a freshly opened dashboard can query immediately.
 */
export function defaultVariableValues(variables: readonly DashboardVariable[]): Record<string, string> {
  const values: Record<string, string> = {}
  for (const variable of variables) {
    values[variable.name] = variable.defaultValue !== '' ? variable.defaultValue : variable.options[0] ?? ''
  }
  return values
}

/**
 * Replaces every `${name}` with the selected value. Unknown variables and variables without
 * a selected value are left untouched, so an unresolved placeholder stays visible instead of
 * silently querying the wrong data. This is a plain string replacement and never evaluates
 * the template.
 */
export function interpolate(template: string, values: Readonly<Record<string, string>>): string {
  return template.replace(PLACEHOLDER_PATTERN, (match, name: string) => {
    const value = values[name]
    return value === undefined || value === '' ? match : value
  })
}

/**
 * Returns widget config with `${var}` placeholders resolved inside the fields that support it
 * (`metricName` for charts, `searchText` for log tables). Other fields pass through unchanged.
 */
export function interpolateWidgetConfig(
  parsed: ParsedWidgetConfig,
  values: Readonly<Record<string, string>>,
): ParsedWidgetConfig {
  if (parsed.type === 'METRIC_CHART') {
    return {
      type: 'METRIC_CHART',
      config: { ...parsed.config, metricName: interpolate(parsed.config.metricName, values) },
    }
  }
  if (parsed.type === 'GAUGE') {
    return {
      type: 'GAUGE',
      config: { ...parsed.config, metricName: interpolate(parsed.config.metricName, values) },
    }
  }
  if (parsed.type === 'LOG_TABLE') {
    const { searchText } = parsed.config
    return {
      type: 'LOG_TABLE',
      config: {
        ...parsed.config,
        searchText: searchText === undefined ? undefined : interpolate(searchText, values),
      },
    }
  }
  return parsed
}

/** Splits the comma-separated options draft into trimmed, non-empty, de-duplicated options. */
export function parseOptionsText(text: string): string[] {
  const seen = new Set<string>()
  const options: string[] = []
  for (const raw of text.split(',')) {
    const option = raw.trim()
    if (option !== '' && !seen.has(option)) {
      seen.add(option)
      options.push(option)
    }
  }
  return options
}

/**
 * Validates variable definitions before saving. Returns the first error message or `null`.
 * Mirrors the backend rules so invalid definitions never reach the API.
 */
export function validateVariables(variables: readonly DashboardVariable[]): string | null {
  if (variables.length > MAX_VARIABLES_PER_DASHBOARD) {
    return `A dashboard may define at most ${MAX_VARIABLES_PER_DASHBOARD} variables.`
  }

  const seen = new Set<string>()
  for (const variable of variables) {
    const name = variable.name.trim()
    if (name === '') {
      return 'Every variable needs a name.'
    }
    if (name.length > MAX_VARIABLE_NAME_LENGTH) {
      return `Variable names must be at most ${MAX_VARIABLE_NAME_LENGTH} characters.`
    }
    if (!isValidVariableName(name)) {
      return `Variable name "${name}" may contain only letters, digits and underscores.`
    }
    if (seen.has(name)) {
      return `Duplicate variable name "${name}".`
    }
    seen.add(name)

    if (variable.options.length === 0) {
      return `Variable "${name}" needs at least one option.`
    }
    if (variable.options.length > MAX_VARIABLE_OPTIONS) {
      return `Variable "${name}" may have at most ${MAX_VARIABLE_OPTIONS} options.`
    }
    if (variable.options.some((option) => option.length > MAX_VARIABLE_OPTION_LENGTH)) {
      return `Options must be at most ${MAX_VARIABLE_OPTION_LENGTH} characters.`
    }
    if (variable.defaultValue !== '' && !variable.options.includes(variable.defaultValue)) {
      return `Default value of "${name}" must be one of its options.`
    }
  }

  return null
}
