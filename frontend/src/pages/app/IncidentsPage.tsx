import { useMemo, useState } from 'react'
import { CheckCheck, ExternalLink, MessageSquare, Siren } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import type { AlertIncident, IncidentStatus } from '../../api/alerts'
import {
  useAcknowledgeIncidentMutation,
  useAlertIncidentsQuery,
  useResolveIncidentMutation,
  useUpdateIncidentNotesMutation,
} from '../../api/alerts'
import { ApiError } from '../../api/client'
import { IncidentStatusBadge } from '../../components/alerts/IncidentStatusBadge'
import { MaintenanceStatusBanner } from '../../components/alerts/MaintenanceStatusBanner'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/dialog'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { Skeleton } from '../../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { logExplorerHref } from '../../features/alerts/incidentLinks'
import { MAX_INCIDENT_NOTE_LENGTH, validateIncidentNote } from '../../features/alerts/incidentNotes'
import { useCanManage } from '../../features/organizations/useCanManage'
import { formatDateTime } from '../../lib/datetime'
import { useDocumentVisible } from '../../lib/useDocumentVisible'

const REFRESH_INTERVAL_MS = 30_000
const INCIDENTS_LIMIT = 100

type IncidentFilter = 'ALL' | IncidentStatus

const FILTER_OPTIONS: { value: IncidentFilter; label: string }[] = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'FIRING', label: 'Firing' },
  { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
  { value: 'RESOLVED', label: 'Resolved' },
]

function actorLabel(userId: number | null, name: string | null): string | null {
  if (name) {
    return name
  }
  return userId === null ? null : `User #${userId}`
}

export default function IncidentsPage() {
  const canManage = useCanManage()
  const isVisible = useDocumentVisible()
  const refetchInterval = isVisible ? REFRESH_INTERVAL_MS : false
  const [statusFilter, setStatusFilter] = useState<IncidentFilter>('ALL')

  const queryStatus: IncidentStatus | undefined = statusFilter === 'ALL' ? undefined : statusFilter
  const { data, isLoading, isError, error, refetch, isFetching } = useAlertIncidentsQuery(
    INCIDENTS_LIMIT,
    queryStatus,
  )

  const acknowledgeMutation = useAcknowledgeIncidentMutation()
  const resolveMutation = useResolveIncidentMutation()
  const notesMutation = useUpdateIncidentNotesMutation()

  const [notesTarget, setNotesTarget] = useState<AlertIncident | null>(null)
  const [notesDraft, setNotesDraft] = useState('')
  const [notesError, setNotesError] = useState<string | null>(null)

  const incidents = data ?? []
  const counts = useMemo(
    () => ({
      firing: incidents.filter((incident) => incident.status === 'FIRING').length,
      acknowledged: incidents.filter((incident) => incident.status === 'ACKNOWLEDGED').length,
      resolved: incidents.filter((incident) => incident.status === 'RESOLVED').length,
    }),
    [incidents],
  )

  const handleAcknowledge = async (incident: AlertIncident) => {
    try {
      await acknowledgeMutation.mutateAsync(incident.id)
      toast.success('Incident acknowledged.')
    } catch (acknowledgeError) {
      toast.error(
        acknowledgeError instanceof ApiError
          ? acknowledgeError.message
          : 'Could not acknowledge the incident.',
      )
    }
  }

  const handleResolve = async (incident: AlertIncident) => {
    try {
      await resolveMutation.mutateAsync(incident.id)
      toast.success('Incident resolved.')
    } catch (resolveError) {
      toast.error(
        resolveError instanceof ApiError ? resolveError.message : 'Could not resolve the incident.',
      )
    }
  }

  const openNotes = (incident: AlertIncident) => {
    setNotesTarget(incident)
    setNotesDraft(incident.notes ?? '')
    setNotesError(null)
  }

  const closeNotes = () => {
    if (notesMutation.isPending) {
      return
    }
    setNotesTarget(null)
    setNotesDraft('')
    setNotesError(null)
  }

  const saveNotes = async () => {
    if (!notesTarget) {
      return
    }
    const value = notesDraft.trim()
    const validationError = validateIncidentNote(value)
    if (validationError) {
      setNotesError(validationError)
      return
    }

    try {
      await notesMutation.mutateAsync({ id: notesTarget.id, notes: value })
      toast.success(value === '' ? 'Incident notes cleared.' : 'Incident notes saved.')
      setNotesTarget(null)
      setNotesDraft('')
      setNotesError(null)
    } catch (notesSaveError) {
      setNotesError(
        notesSaveError instanceof ApiError
          ? notesSaveError.message
          : 'Could not save the incident notes.',
      )
    }
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Alerting</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Incidents</h1>
          <p className="text-sm text-slate-400">
            Incidents opened by alert rules, newest first. Acknowledge what you are working on and
            close it when the issue is fixed.
          </p>
        </div>
        {!isLoading && !isError ? (
          <div className="flex flex-wrap items-center gap-2" role="status" aria-label="Incident summary">
            {statusFilter === 'ALL' ? (
              <>
                <Badge variant={counts.firing > 0 ? 'danger' : 'success'}>{counts.firing} firing</Badge>
                <Badge variant={counts.acknowledged > 0 ? 'warning' : 'muted'}>
                  {counts.acknowledged} acknowledged
                </Badge>
                <Badge variant="muted">{counts.resolved} resolved</Badge>
              </>
            ) : (
              <Badge variant="muted">
                {incidents.length} {statusFilter.toLowerCase()}
              </Badge>
            )}
          </div>
        ) : null}
      </header>

      <MaintenanceStatusBanner />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-500">
          {isVisible
            ? 'Auto-refreshes every 30 seconds.'
            : 'Auto-refresh paused while this tab is hidden.'}
        </p>
        <div className="flex items-center gap-2">
          <Label htmlFor="incident-status-filter" className="text-xs text-slate-400">
            Status
          </Label>
          <Select
            value={statusFilter}
            onValueChange={(value) => setStatusFilter(value as IncidentFilter)}
          >
            <SelectTrigger id="incident-status-filter" className="h-9 w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FILTER_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

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
        <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
          <p className="text-sm text-slate-300">
            {statusFilter === 'ALL'
              ? 'No incidents recorded yet.'
              : `No ${statusFilter.toLowerCase()} incidents.`}
          </p>
        </div>
      ) : (
        <div className="rounded-2xl border border-white/10 bg-panel/40 p-1">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Status</TableHead>
                <TableHead>Rule</TableHead>
                <TableHead>Triggered</TableHead>
                <TableHead>Acknowledged</TableHead>
                <TableHead>Resolved</TableHead>
                {canManage ? <TableHead className="text-right">Actions</TableHead> : null}
              </TableRow>
            </TableHeader>
            <TableBody>
              {incidents.map((incident) => {
                const acknowledgedByName = actorLabel(incident.acknowledgedBy, incident.acknowledgedByName)
                const resolvedByName = actorLabel(incident.resolvedBy, incident.resolvedByName)
                const acknowledging =
                  acknowledgeMutation.isPending && acknowledgeMutation.variables === incident.id
                const resolving = resolveMutation.isPending && resolveMutation.variables === incident.id
                const explorerHref = logExplorerHref(incident)

                return (
                  <TableRow key={incident.id}>
                    <TableCell>
                      <IncidentStatusBadge status={incident.status} />
                    </TableCell>
                    <TableCell className="min-w-[14rem]">
                      <p className="font-medium text-slate-100">{incident.ruleName}</p>
                      {explorerHref ? (
                        <Link
                          to={explorerHref}
                          className="mt-1 inline-flex items-center gap-1 text-xs text-cyan-300 hover:text-cyan-200"
                        >
                          <ExternalLink className="h-3 w-3" aria-hidden="true" />
                          View matching logs
                        </Link>
                      ) : null}
                      {incident.notes ? (
                        <p className="mt-1 max-w-xs truncate text-xs text-slate-400" title={incident.notes}>
                          {incident.notes}
                        </p>
                      ) : null}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-slate-300">
                      {formatDateTime(incident.triggeredAt)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-slate-300">
                      {acknowledgedByName ? (
                        <>
                          <span className="block">{acknowledgedByName}</span>
                          {incident.acknowledgedAt ? (
                            <span className="block text-xs text-slate-500">
                              {formatDateTime(incident.acknowledgedAt)}
                            </span>
                          ) : null}
                        </>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-slate-300">
                      {incident.resolvedAt ? (
                        <>
                          <span className="block">{resolvedByName ?? 'Automatic'}</span>
                          <span className="block text-xs text-slate-500">
                            {formatDateTime(incident.resolvedAt)}
                          </span>
                        </>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <div className="flex flex-wrap items-center justify-end gap-2">
                          {incident.status === 'FIRING' ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="secondary"
                              onClick={() => void handleAcknowledge(incident)}
                              disabled={acknowledging || resolving}
                            >
                              {acknowledging ? 'Acknowledging…' : 'Acknowledge'}
                            </Button>
                          ) : null}
                          {incident.status !== 'RESOLVED' ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => void handleResolve(incident)}
                              disabled={acknowledging || resolving}
                            >
                              <CheckCheck className="h-4 w-4" aria-hidden="true" />
                              {resolving ? 'Resolving…' : 'Resolve'}
                            </Button>
                          ) : null}
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            onClick={() => openNotes(incident)}
                          >
                            <MessageSquare className="h-4 w-4" aria-hidden="true" />
                            Notes
                          </Button>
                        </div>
                      </TableCell>
                    ) : null}
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={notesTarget !== null} onOpenChange={(open) => (!open ? closeNotes() : undefined)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Incident notes</DialogTitle>
            <DialogDescription>
              Add plain-text context for your team, for example the suspected root cause or the fix
              in progress. Empty notes clear the existing value.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 space-y-2">
            <Label htmlFor="incident-notes">Notes</Label>
            <textarea
              id="incident-notes"
              value={notesDraft}
              onChange={(event) => {
                setNotesDraft(event.target.value)
                setNotesError(null)
              }}
              maxLength={MAX_INCIDENT_NOTE_LENGTH}
              rows={6}
              aria-invalid={notesError !== null}
              className="flex w-full rounded-md border border-white/10 bg-canvas-2 px-3 py-2 text-sm text-slate-100 transition-colors placeholder:text-slate-500 focus-visible:border-cyan-400/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50 aria-[invalid=true]:border-red-500/60 aria-[invalid=true]:focus-visible:ring-red-500/40"
            />
            <div className="flex items-center justify-between gap-3 text-xs text-slate-500">
              <span>Plain text only; HTML is not allowed.</span>
              <span>
                {notesDraft.length}/{MAX_INCIDENT_NOTE_LENGTH}
              </span>
            </div>
            {notesError ? (
              <p role="alert" className="text-xs text-red-300">
                {notesError}
              </p>
            ) : null}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={closeNotes}
              disabled={notesMutation.isPending}
            >
              Cancel
            </Button>
            <Button type="button" onClick={() => void saveNotes()} disabled={notesMutation.isPending}>
              {notesMutation.isPending ? 'Saving…' : 'Save note'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}

function IncidentsSkeleton() {
  return (
    <div className="space-y-2 rounded-2xl border border-white/10 bg-panel/40 p-4" aria-busy="true">
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
