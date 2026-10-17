import { Siren } from 'lucide-react'
import { useAlertIncidentsQuery } from '../../api/alerts'
import { ApiError } from '../../api/client'
import { IncidentStatusBadge } from '../../components/alerts/IncidentStatusBadge'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { formatDateTime } from '../../lib/datetime'
import { useDocumentVisible } from '../../lib/useDocumentVisible'

const REFRESH_INTERVAL_MS = 30_000
const INCIDENTS_LIMIT = 100

export default function IncidentsPage() {
  const isVisible = useDocumentVisible()
  const refetchInterval = isVisible ? REFRESH_INTERVAL_MS : false
  const { data, isLoading, isError, error, refetch, isFetching } = useAlertIncidentsQuery(INCIDENTS_LIMIT)

  const incidents = data ?? []
  const firingCount = incidents.filter((incident) => incident.status === 'FIRING').length

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Alerting</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Incidents</h1>
          <p className="text-sm text-slate-400">Incidents opened by alert rules, newest first.</p>
        </div>
        {!isLoading && !isError ? (
          <div className="flex flex-wrap items-center gap-2" role="status" aria-label="Incident summary">
            <Badge variant={firingCount > 0 ? 'danger' : 'success'}>{firingCount} firing</Badge>
            <Badge variant="muted">{incidents.length - firingCount} resolved</Badge>
          </div>
        ) : null}
      </header>

      {isLoading ? (
        <IncidentsSkeleton />
      ) : isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load incidents</h2>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError ? error.message : 'Something went wrong while loading incidents.'}
          </p>
          <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : incidents.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-[#0b101b]/40 p-10 text-center">
          <p className="text-sm text-slate-300">No incidents recorded yet.</p>
        </div>
      ) : (
        <div className="space-y-2">
          <p className="text-xs text-slate-500">
            {isVisible ? 'Auto-refreshes every 30 seconds.' : 'Auto-refresh paused while this tab is hidden.'}
          </p>
          <div className="rounded-2xl border border-white/10 bg-[#0b101b]/40 p-1">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Status</TableHead>
                  <TableHead>Rule</TableHead>
                  <TableHead>Triggered</TableHead>
                  <TableHead>Resolved</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {incidents.map((incident) => (
                  <TableRow key={incident.id}>
                    <TableCell>
                      <IncidentStatusBadge status={incident.status} />
                    </TableCell>
                    <TableCell className="font-medium text-slate-100">{incident.ruleName}</TableCell>
                    <TableCell className="whitespace-nowrap text-slate-300">
                      {formatDateTime(incident.triggeredAt)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-slate-300">
                      {incident.resolvedAt ? formatDateTime(incident.resolvedAt) : '—'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      )}
    </section>
  )
}

function IncidentsSkeleton() {
  return (
    <div className="space-y-2 rounded-2xl border border-white/10 bg-[#0b101b]/40 p-4" aria-busy="true">
      {[0, 1, 2].map((row) => (
        <div key={row} className="flex items-center gap-4">
          <Siren className="h-4 w-4 text-slate-600" aria-hidden="true" />
          <Skeleton className="h-6 w-24 rounded-full" />
          <Skeleton className="h-4 flex-1" />
        </div>
      ))}
    </div>
  )
}
