import { useMetricNamesQuery } from '../../api/metrics'
import { Input } from '../ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface MetricNameSelectProps {
  applicationId: number | null
  value: string
  onChange: (value: string) => void
  id?: string
  disabled?: boolean
}

export function MetricNameSelect({ applicationId, value, onChange, id, disabled }: MetricNameSelectProps) {
  const { data, isLoading, isError } = useMetricNamesQuery(applicationId)
  const names = data ?? []

  if (applicationId === null) {
    return (
      <Select disabled>
        <SelectTrigger id={id} aria-label="Metric name">
          <SelectValue placeholder="Select an application first" />
        </SelectTrigger>
        <SelectContent />
      </Select>
    )
  }

  if (isLoading) {
    return (
      <Select disabled>
        <SelectTrigger id={id} aria-label="Metric name">
          <SelectValue placeholder="Loading metrics…" />
        </SelectTrigger>
        <SelectContent />
      </Select>
    )
  }

  if (isError || names.length === 0) {
    return (
      <Input
        id={id}
        placeholder={isError ? 'Could not load metrics — type a name' : 'No metrics yet — type a name'}
        autoComplete="off"
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
    )
  }

  const options = value !== '' && !names.includes(value) ? [value, ...names] : names

  return (
    <Select value={value === '' ? undefined : value} onValueChange={onChange} disabled={disabled}>
      <SelectTrigger id={id} aria-label="Metric name">
        <SelectValue placeholder="Select a metric" />
      </SelectTrigger>
      <SelectContent>
        {options.map((name) => (
          <SelectItem key={name} value={name}>
            {name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
