import { useEffect, useState } from 'react'
import { MoreHorizontal, Server } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { AgentNode } from '../../api/agents'
import { useAgentsQuery } from '../../api/agents'
import type { Application } from '../../api/applications'
import { useApplicationsQuery } from '../../api/useApplications'
import { ApiError } from '../../api/client'
import { AgentStatusBadge } from '../../components/agents/AgentStatusBadge'
import { RegenerateKeyDialog } from '../../components/applications/RegenerateKeyDialog'
import { ApplicationSelect } from '../../components/filters/ApplicationSelect'
import { Button } from '../../components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu'
import { Skeleton } from '../../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { resolveAgentStatus } from '../../features/agents/status'
import { MAX_SEARCH_TEXT_LENGTH } from '../../features/logs/schemas'
import { useCanManage } from '../../features/organizations/useCanManage'
import { formatDateTime } from '../../lib/datetime'
import { useDocumentVisible } from '../../lib/useDocumentVisible'

const REFRESH_INTERVAL_MS = 30_000
const LOGS_WINDOW_MS = 60 * 60 * 1000

export default function AgentFleetPage() {
  const canManage = useCanManage()
  const isVisible = useDocumentVisible()
  const refetchInterval = isVisible ? REFRESH_INTERVAL_MS : false

  const applicationsQuery = useApplicationsQuery()
  const applications = applicationsQuery.data ?? []

  const navigate = useNavigate()
  const [applicationId, setApplicationId] = useState<number | null>(null)
  const [revokeTarget, setRevokeTarget] = useState<Application | null>(null)

  // Select the first application by default so the page is useful without an extra click.
  useEffect(() => {
    if (applicationId === null && applications.length > 0) {
      setApplicationId(applications[0].id)
    }
  }, [applicationId, applications])

  const agentsQuery = useAgentsQuery(applicationId, refetchInterval)

  // Recompute relative statuses on a timer so an idle agent decays to Offline even when the
  // backend keeps returning the same lastSeenAt (react-query would not re-render otherwise).
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    if (!isVisible) {
      return
    }
    const id = window.setInterval(() => setNow(new Date()), REFRESH_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [isVisible])

  const selectedApplication = applications.find((application) => application.id === applicationId) ?? null
  const agents = agentsQuery.data ?? []

  const openLogs = (agent: AgentNode) => {
    if (applicationId === null) {
      return
    }
    const to = new Date()
    const from = new Date(to.getTime() - LOGS_WINDOW_MS)
    const params = new URLSearchParams({
      applicationId: String(applicationId),
      from: from.toISOString(),
      to: to.toISOString(),
      searchText: agent.hostname.slice(0, MAX_SEARCH_TEXT_LENGTH),
    })
    navigate(`/app/logs?${params.toString()}`)
  }

  const showAgentsTable = applicationId !== null && !agentsQuery.isLoading && !agentsQuery.isError

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Agents</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Agent fleet</h1>
          <p className="text-sm text-slate-400">
            Installed agents reporting telemetry for an application, with their last activity.
          </p>
        </div>
        {canManage && selectedApplication ? (
          <Button type="button" variant="outline" onClick={() => setRevokeTarget(selectedApplication)}>
            Revoke API key
          </Button>
        ) : null}
      </header>

      <div className="max-w-sm space-y-2">
        <label htmlFor="agents-application" className="text-xs font-medium text-slate-400">
          Application
        </label>
        <ApplicationSelect
          id="agents-application"
          value={applicationId}
          onChange={setApplicationId}
          disabled={applicationsQuery.isError}
        />
      </div>

      {applicationId === null ? (
        <EmptyState message="Select an application to see its agents." />
      ) : agentsQuery.isLoading ? (
        <AgentsSkeleton />
      ) : agentsQuery.isError ? (
        <ErrorState
          error={agentsQuery.error}
          onRetry={() => agentsQuery.refetch()}
          isRetrying={agentsQuery.isFetching}
        />
      ) : agents.length === 0 ? (
        <EmptyState message="No agents have reported for this application yet." />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-white/10 bg-panel/60">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Hostname</TableHead>
                <TableHead>Version</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Last seen</TableHead>
                <TableHead className="w-16 text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {agents.map((agent) => (
                <TableRow key={agent.id}>
                  <TableCell className="font-medium text-slate-100">
                    <span className="flex items-center gap-2">
                      <Server className="h-4 w-4 text-slate-500" aria-hidden="true" />
                      {agent.hostname}
                    </span>
                  </TableCell>
                  <TableCell className="text-slate-400">{agent.agentVersion ?? '—'}</TableCell>
                  <TableCell>
                    <AgentStatusBadge status={resolveAgentStatus(agent.lastSeenAt, now)} />
                  </TableCell>
                  <TableCell className="whitespace-nowrap text-slate-400">
                    {formatDateTime(agent.lastSeenAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <DropdownMenu modal={false}>
                      <DropdownMenuTrigger asChild>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label={`Actions for ${agent.hostname}`}
                          className="h-8 w-8"
                        >
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => openLogs(agent)}>View agent logs</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          onSelect={() => setRevokeTarget(selectedApplication)}
                          className="text-red-300 focus:text-red-200"
                        >
                          Revoke API key
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {showAgentsTable && agents.length > 0 ? (
        <p className="text-xs text-slate-500" role="status">
          {isVisible ? 'Auto-refreshes every 30 seconds.' : 'Auto-refresh paused while this tab is hidden.'}
        </p>
      ) : null}

      <RegenerateKeyDialog
        open={revokeTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRevokeTarget(null)
          }
        }}
        application={revokeTarget}
      />
    </section>
  )
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex h-72 items-center justify-center rounded-2xl border border-dashed border-white/10 bg-panel/40">
      <p className="text-sm text-slate-400">{message}</p>
    </div>
  )
}

function ErrorState({
  error,
  onRetry,
  isRetrying,
}: {
  error: unknown
  onRetry: () => void
  isRetrying: boolean
}) {
  const message =
    error instanceof ApiError && error.status === 403
      ? 'You do not have permission to view this application.'
      : error instanceof ApiError
        ? error.message
        : 'Something went wrong while loading agents.'

  return (
    <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
      <h2 className="font-display text-lg font-semibold text-red-200">Could not load agents</h2>
      <p className="mt-2 text-sm text-slate-400">{message}</p>
      <Button type="button" variant="outline" className="mt-4" onClick={onRetry} disabled={isRetrying}>
        {isRetrying ? 'Retrying…' : 'Retry'}
      </Button>
    </div>
  )
}

function AgentsSkeleton() {
  return (
    <div aria-busy="true" className="overflow-hidden rounded-2xl border border-white/10 bg-panel/60 p-4">
      <div className="space-y-3">
        {[0, 1, 2].map((row) => (
          <div key={row} className="flex items-center gap-4">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-4 w-16" />
            <Skeleton className="h-6 w-20 rounded-full" />
            <Skeleton className="ml-auto h-8 w-8 rounded-md" />
          </div>
        ))}
      </div>
    </div>
  )
}
