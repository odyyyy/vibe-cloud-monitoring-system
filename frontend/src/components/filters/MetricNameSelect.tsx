import { useMemo, useState } from 'react'
import { useMetricNamesQuery } from '../../api/metrics'
import { BUILT_IN_METRICS, metricColor, metricLabel } from '../../features/metrics/catalog'
import { Input } from '../ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

const CUSTOM_VALUE = '__custom__'

interface MetricNameSelectProps {
  applicationId: number | null
  value: string
  onChange: (value: string) => void
  id?: string
  disabled?: boolean
}

interface MetricOption {
  name: string
  label: string
  color: string
}

export function MetricNameSelect({ applicationId, value, onChange, id, disabled }: MetricNameSelectProps) {
  const { data, isLoading, isError } = useMetricNamesQuery(applicationId)
  const discovered = data ?? []
  const [isTypingCustom, setIsTypingCustom] = useState(false)

  const options = useMemo<MetricOption[]>(() => {
    const seen = new Set<string>()
    const result: MetricOption[] = []
    const push = (name: string) => {
      if (seen.has(name)) {
        return
      }
      seen.add(name)
      result.push({ name, label: metricLabel(name), color: metricColor(name) })
    }

    for (const metric of BUILT_IN_METRICS) {
      push(metric.name)
    }
    for (const name of discovered) {
      push(name)
    }
    return result
  }, [discovered])

  const isCustomValue = value !== '' && !options.some((option) => option.name === value)

  if (isTypingCustom || isCustomValue) {
    return (
      <div className="space-y-1.5">
        <Input
          id={id}
          aria-label="Metric name"
          placeholder="e.g. my.custom.metric"
          autoComplete="off"
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        />
        <button
          type="button"
          className="text-xs text-cyan-300 underline-offset-4 hover:underline disabled:opacity-50"
          disabled={disabled}
          onClick={() => {
            setIsTypingCustom(false)
            onChange('')
          }}
        >
          Choose from list
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-1.5">
      <Select
        // Always pass a string: passing `undefined` would flip Radix Select into
        // uncontrolled mode, so after clearing the value it would keep showing the
        // previously selected item while the form state is empty. Radix renders the
        // placeholder for the empty string.
        value={value}
        onValueChange={(next) => {
          if (next === CUSTOM_VALUE) {
            setIsTypingCustom(true)
            return
          }
          onChange(next)
        }}
        disabled={disabled}
      >
        <SelectTrigger id={id} aria-label="Metric name">
          <SelectValue placeholder="Select a metric" />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.name} value={option.name}>
              <span className="flex items-center gap-2">
                <span
                  aria-hidden="true"
                  className="h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: option.color }}
                />
                <span>{option.label}</span>
                {option.label !== option.name ? (
                  <span className="mono text-[10px] text-slate-500">{option.name}</span>
                ) : null}
              </span>
            </SelectItem>
          ))}
          <SelectItem value={CUSTOM_VALUE}>Custom metric name…</SelectItem>
        </SelectContent>
      </Select>

      {applicationId !== null && isLoading ? (
        <p className="text-xs text-slate-500">Loading metrics reported by this application…</p>
      ) : null}
      {applicationId !== null && !isLoading && isError ? (
        <p className="text-xs text-amber-300/80">
          Could not load reported metrics — pick a built-in metric or type a name.
        </p>
      ) : null}
      {applicationId !== null && !isLoading && !isError && discovered.length === 0 ? (
        <p className="text-xs text-slate-500">
          No metrics reported yet — the built-in host metrics are listed above.
        </p>
      ) : null}
    </div>
  )
}
