import { Activity } from 'lucide-react'

interface StatusWidgetProps {
  applicationId: number
}

/**
 * Placeholder status card. Service health requires the health-check API, which is
 * out of scope for this task; the widget intentionally reports that it is a
 * placeholder instead of implying a healthy state.
 */
export function StatusWidget({ applicationId }: StatusWidgetProps) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-white/10 bg-white/[0.02] p-4 text-center">
      <Activity className="h-6 w-6 text-slate-500" aria-hidden="true" />
      <p className="text-sm text-slate-300">Status placeholder</p>
      <p className="text-xs text-slate-500">
        Service health for application #{applicationId} will appear here once health checks are available.
      </p>
    </div>
  )
}
