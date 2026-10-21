import { Link } from 'react-router-dom'
import { Wrench } from 'lucide-react'
import { useMaintenanceWindowsQuery } from '../../api/maintenanceWindows'
import { activeMaintenanceWindows } from '../../features/maintenance/window'
import { useDocumentVisible } from '../../lib/useDocumentVisible'

/**
 * Shows a non-blocking notice on alerting pages while at least one maintenance window is active.
 * Windows are polled only while the tab is visible, and nothing is rendered on load or error so a
 * stale response can never claim that alerts are muted.
 */
export function MaintenanceStatusBanner() {
  const isVisible = useDocumentVisible()
  const query = useMaintenanceWindowsQuery(isVisible ? 30_000 : false)
  const active = activeMaintenanceWindows(query.data ?? [])

  if (active.length === 0) {
    return null
  }

  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-3 rounded-2xl border border-amber-400/30 bg-amber-400/5 px-4 py-3"
    >
      <Wrench className="h-5 w-5 shrink-0 text-amber-200" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-amber-100">Maintenance mode is active</p>
        <p className="text-xs text-amber-200/70">
          {active.length === 1
            ? `“${active[0]!.name}” is muting alert incidents and notifications.`
            : `${active.length} maintenance windows are muting alert incidents and notifications.`}
        </p>
      </div>
      <Link
        to="/app/maintenance"
        className="text-xs font-medium text-amber-200 underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300/60"
      >
        Review windows
      </Link>
    </div>
  )
}
