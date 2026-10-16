import { cn } from '../../lib/utils'
import {
  TIME_RANGE_PRESETS,
  rangeDurationMs,
  resolvePresetRange,
  type TimeRange,
} from '../../features/metrics/timeRange'

interface TimeRangePickerProps {
  value: TimeRange | null
  onChange: (range: TimeRange) => void
  disabled?: boolean
}

const HOUR_MS = 60 * 60 * 1000

export function TimeRangePicker({ value, onChange, disabled }: TimeRangePickerProps) {
  const activeId = value
    ? TIME_RANGE_PRESETS.find((preset) => {
        const duration = rangeDurationMs(value.from, value.to)
        return duration !== null && Math.abs(duration - preset.hours * HOUR_MS) < 60 * 1000
      })?.id
    : undefined

  return (
    <div role="group" aria-label="Time range" className="flex flex-wrap gap-1.5">
      {TIME_RANGE_PRESETS.map((preset) => {
        const active = preset.id === activeId
        return (
          <button
            key={preset.id}
            type="button"
            disabled={disabled}
            aria-pressed={active}
            onClick={() => onChange(resolvePresetRange(preset))}
            className={cn(
              'rounded-md border px-3 py-1.5 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50 disabled:cursor-not-allowed disabled:opacity-50',
              active
                ? 'border-cyan-400/40 bg-cyan-400/10 text-cyan-200'
                : 'border-white/10 bg-white/[0.02] text-slate-400 hover:bg-white/5 hover:text-slate-200',
            )}
          >
            {preset.label}
          </button>
        )
      })}
    </div>
  )
}
