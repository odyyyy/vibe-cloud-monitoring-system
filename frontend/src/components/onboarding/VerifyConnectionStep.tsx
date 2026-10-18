import { useEffect, useState } from 'react'
import { Loader2, TriangleAlert } from 'lucide-react'
import type { AgentNode } from '../../api/agents'
import { useAgentsQuery } from '../../api/agents'
import type { Application } from '../../api/applications'
import { ApiError } from '../../api/client'
import { Button } from '../ui/button'
import { DialogFooter } from '../ui/dialog'
import { resolveAgentStatus } from '../../features/agents/status'

/** How often the wizard asks the backend whether the agent has reported yet. */
export const VERIFY_POLL_INTERVAL_MS = 4_000
/** Give up polling after this long so the step never spins forever. */
export const VERIFY_TIMEOUT_MS = 2 * 60 * 1000

interface VerifyConnectionStepProps {
  application: Application
  onConnected: (agent: AgentNode) => void
  onDismiss: () => void
}

/**
 * Polls the agent fleet for the selected application until an agent is seen online. Polling stops
 * after `VERIFY_TIMEOUT_MS`; the user can retry manually or leave the wizard open.
 */
export function VerifyConnectionStep({ application, onConnected, onDismiss }: VerifyConnectionStepProps) {
  const [attempt, setAttempt] = useState(0)
  const [timedOut, setTimedOut] = useState(false)

  const agentsQuery = useAgentsQuery(application.id, timedOut ? false : VERIFY_POLL_INTERVAL_MS)
  const agents = agentsQuery.isSuccess ? agentsQuery.data : null

  useEffect(() => {
    setTimedOut(false)
    const id = window.setTimeout(() => setTimedOut(true), VERIFY_TIMEOUT_MS)
    return () => window.clearTimeout(id)
  }, [attempt])

  useEffect(() => {
    if (!agents) {
      return
    }
    const now = new Date()
    const connected = agents.find((agent) => resolveAgentStatus(agent.lastSeenAt, now) === 'ONLINE')
    if (connected) {
      onConnected(connected)
    }
  }, [agents, onConnected])

  const handleCheckAgain = () => {
    setAttempt((value) => value + 1)
    void agentsQuery.refetch()
  }

  const errorMessage = agentsQuery.isError
    ? agentsQuery.error instanceof ApiError
      ? agentsQuery.error.message
      : 'Something went wrong while checking for telemetry.'
    : null

  return (
    <>
      <div className="mt-6 space-y-4">
        {timedOut ? (
          <div role="status" className="rounded-xl border border-amber-400/20 bg-amber-400/5 p-4">
            <p className="flex items-center gap-2 text-sm font-medium text-amber-200">
              <TriangleAlert className="h-4 w-4 shrink-0" aria-hidden="true" />
              Still no telemetry after 2 minutes.
            </p>
            <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-slate-400">
              <li>
                Check the container is running: <code className="mono text-slate-300">docker ps</code>
              </li>
              <li>The agent must be able to reach this platform over the network.</li>
              <li>
                Verify the API key in the command is the one shown for{' '}
                <span className="text-slate-300">{application.name}</span>.
              </li>
            </ul>
          </div>
        ) : (
          <div className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.02] p-4">
            <Loader2 className="h-5 w-5 shrink-0 animate-spin text-cyan-300" aria-hidden="true" />
            <div>
              <p className="text-sm text-slate-200">
                Listening for the first telemetry from{' '}
                <span className="text-slate-100">{application.name}</span>…
              </p>
              <p className="mt-0.5 text-xs text-slate-500">
                This usually takes a few seconds. The wizard stops waiting after 2 minutes.
              </p>
            </div>
          </div>
        )}

        {errorMessage ? (
          <p role="alert" className="text-sm text-red-300">
            {errorMessage}
          </p>
        ) : null}
      </div>

      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onDismiss}>
          Set up later
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={handleCheckAgain}
          disabled={agentsQuery.isFetching}
        >
          {agentsQuery.isFetching ? 'Checking…' : 'Check now'}
        </Button>
      </DialogFooter>
    </>
  )
}
