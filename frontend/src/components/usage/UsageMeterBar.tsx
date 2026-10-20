import type { LucideIcon } from 'lucide-react'
import {
  clampPercentage,
  computeUsagePercentage,
  formatUsageCount,
  formatUsagePercentage,
  usageLevel,
  type UsageLevel,
} from '../../features/usage/quota'
import { cn } from '../../lib/utils'

interface UsageMeterBarProps {
  label: string
  icon: LucideIcon
  used: number
  limit: number
  percentage: number
}

const LEVEL_BAR_CLASS: Record<UsageLevel, string> = {
  ok: 'bg-emerald-400',
  warning: 'bg-amber-400',
  critical: 'bg-red-500',
}

const LEVEL_TEXT_CLASS: Record<UsageLevel, string> = {
  ok: 'text-emerald-300',
  warning: 'text-amber-300',
  critical: 'text-red-300',
}

const LEVEL_LABEL: Record<UsageLevel, string> = {
  ok: 'Within quota',
  warning: 'Approaching quota',
  critical: 'Quota nearly reached',
}

export function UsageMeterBar({ label, icon: Icon, used, limit, percentage }: UsageMeterBarProps) {
  // Derive the share from the raw counters instead of trusting the server value alone: a
  // rounded/stale backend percentage can be 0 while `used` is already non-zero.
  const effectivePercentage = limit > 0 ? computeUsagePercentage(used, limit) : percentage
  const level = usageLevel(effectivePercentage)
  const width = clampPercentage(effectivePercentage)
  const percentageLabel = formatUsagePercentage(effectivePercentage)

  return (
    <div className="rounded-2xl border border-white/10 bg-panel/60 p-5">
      <div className="flex items-center gap-2 text-slate-400">
        <Icon className="h-4 w-4" aria-hidden="true" />
        <h3 className="text-sm font-medium text-slate-200">{label}</h3>
      </div>

      <div className="mt-4 flex items-baseline justify-between gap-2">
        <p className="text-2xl font-semibold text-slate-100">{formatUsageCount(used)}</p>
        <p className="text-xs text-slate-500">of {formatUsageCount(limit)}</p>
      </div>

      <div
        role="progressbar"
        aria-label={`${label} usage`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(width * 100) / 100}
        aria-valuetext={`${percentageLabel} used`}
        className="mt-3 h-2 w-full overflow-hidden rounded-full bg-white/10"
      >
        <div
          className={cn('h-full rounded-full transition-[width] duration-300', LEVEL_BAR_CLASS[level])}
          style={{ width: `${width}%` }}
        />
      </div>

      <div className="mt-2 flex items-center justify-between gap-2 text-xs">
        <span className={cn('font-medium', LEVEL_TEXT_CLASS[level])}>{percentageLabel} used</span>
        <span className="text-slate-500">{LEVEL_LABEL[level]}</span>
      </div>
    </div>
  )
}
