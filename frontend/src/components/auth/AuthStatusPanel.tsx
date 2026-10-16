import type { ReactNode } from 'react'

interface AuthStatusPanelProps {
  icon: ReactNode
  title: string
  description: string
}

export function AuthStatusPanel({ icon, title, description }: AuthStatusPanelProps) {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-white/10 bg-white/5 p-4">
      <div className="mt-0.5 shrink-0">{icon}</div>
      <div className="space-y-1">
        <p className="text-sm font-medium text-slate-100">{title}</p>
        <p className="text-sm text-slate-400">{description}</p>
      </div>
    </div>
  )
}
