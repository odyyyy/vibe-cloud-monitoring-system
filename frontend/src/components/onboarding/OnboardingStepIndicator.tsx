import { Check } from 'lucide-react'
import { cn } from '../../lib/utils'

export type OnboardingStepId = 'create' | 'install' | 'verify' | 'success'

const STEPS: { id: OnboardingStepId; label: string }[] = [
  { id: 'create', label: 'Create' },
  { id: 'install', label: 'Install' },
  { id: 'verify', label: 'Verify' },
  { id: 'success', label: 'Done' },
]

interface OnboardingStepIndicatorProps {
  current: OnboardingStepId
}

export function OnboardingStepIndicator({ current }: OnboardingStepIndicatorProps) {
  const currentIndex = STEPS.findIndex((step) => step.id === current)

  return (
    <ol className="mt-5 flex items-center gap-1.5" aria-label="Setup progress">
      {STEPS.map((step, index) => {
        const isDone = index < currentIndex
        const isCurrent = index === currentIndex
        return (
          <li
            key={step.id}
            className="flex min-w-0 flex-1 items-center gap-2"
            aria-current={isCurrent ? 'step' : undefined}
          >
            <span
              className={cn(
                'flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-[11px] font-semibold',
                isDone
                  ? 'border-cyan-400/40 bg-cyan-400/15 text-cyan-200'
                  : isCurrent
                    ? 'border-cyan-400/60 bg-cyan-400/10 text-cyan-300'
                    : 'border-white/10 bg-white/[0.02] text-slate-500',
              )}
            >
              {isDone ? <Check className="h-3 w-3" aria-hidden="true" /> : index + 1}
            </span>
            <span
              className={cn(
                'hidden truncate text-xs sm:block',
                isCurrent ? 'font-medium text-slate-200' : 'text-slate-500',
              )}
            >
              {step.label}
            </span>
            {index < STEPS.length - 1 ? (
              <span className="h-px min-w-[0.5rem] flex-1 bg-white/10" aria-hidden="true" />
            ) : null}
          </li>
        )
      })}
    </ol>
  )
}
