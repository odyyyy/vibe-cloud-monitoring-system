import { useEffect, useMemo, useState } from 'react'
import { Check, Copy, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import type { Application } from '../../api/applications'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { Button } from '../ui/button'
import { DialogFooter } from '../ui/dialog'
import { Label } from '../ui/label'
import { copyText } from '../../lib/clipboard'
import { buildAgentDockerCommand, resolveBackendUrl } from '../../features/onboarding/dockerCommand'

interface InstallAgentStepProps {
  application: Application
  apiKey: string | null
  applications: Application[]
  onApplicationChange: (application: Application) => void
  onRequestRegenerate: () => void
  onContinue: () => void
  onDismiss: () => void
}

export function InstallAgentStep({
  application,
  apiKey,
  applications,
  onApplicationChange,
  onRequestRegenerate,
  onContinue,
  onDismiss,
}: InstallAgentStepProps) {
  const [copied, setCopied] = useState(false)

  const command = useMemo(
    () => buildAgentDockerCommand({ backendUrl: resolveBackendUrl(), apiKey }),
    [apiKey],
  )

  useEffect(() => {
    if (!copied) {
      return
    }
    const id = window.setTimeout(() => setCopied(false), 2000)
    return () => window.clearTimeout(id)
  }, [copied])

  const handleCopy = async () => {
    const ok = await copyText(command)
    if (ok) {
      setCopied(true)
    } else {
      toast.info('Clipboard is unavailable. Select and copy the command manually.')
    }
  }

  const handleSelect = (applicationId: number | null) => {
    const next = applications.find((item) => item.id === applicationId)
    if (next) {
      onApplicationChange(next)
    }
  }

  return (
    <>
      <div className="mt-6 space-y-4">
        {applications.length > 1 ? (
          <div className="space-y-2">
            <Label htmlFor="onboarding-application">Application</Label>
            <ApplicationSelect
              id="onboarding-application"
              value={application.id}
              onChange={handleSelect}
            />
          </div>
        ) : (
          <p className="text-sm text-slate-400">
            Run this command on the machine or container of{' '}
            <span className="text-slate-200">{application.name}</span>:
          </p>
        )}

        <div className="relative overflow-hidden rounded-xl border border-white/10 bg-black/20">
          <pre className="overflow-x-auto p-4 pb-14 text-xs leading-relaxed text-slate-300 sm:pb-4">
            <code className="mono">{command}</code>
          </pre>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className="absolute bottom-2 left-4 sm:bottom-auto sm:left-auto sm:right-2 sm:top-2"
            onClick={() => {
              void handleCopy()
            }}
          >
            {copied ? (
              <>
                <Check className="h-3.5 w-3.5 text-lime-300" aria-hidden="true" />
                Copied
              </>
            ) : (
              <>
                <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                Copy
              </>
            )}
          </Button>
        </div>

        {apiKey ? (
          <p className="text-xs text-slate-500">
            The command embeds this application&apos;s API key, which is shown only once. Copy the command
            before continuing.
          </p>
        ) : (
          <div className="flex flex-col gap-3 rounded-xl border border-amber-400/20 bg-amber-400/5 p-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-amber-200">
              The API key for this application is no longer shown. Regenerate it to get a fresh key and
              update the command, or replace{' '}
              <code className="mono text-amber-200">REPLACE_WITH_YOUR_API_KEY</code> with an existing key.
            </p>
            <Button type="button" variant="outline" size="sm" className="shrink-0" onClick={onRequestRegenerate}>
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              Regenerate key
            </Button>
          </div>
        )}

        <p className="text-xs text-slate-500">
          The image is built from the <code className="mono text-slate-400">agent/</code> directory (
          <code className="mono text-slate-400">docker build -t vibemonitoring/agent:latest .</code>). The
          agent needs network access to this platform on{' '}
          <code className="mono text-slate-400">{resolveBackendUrl()}</code>.
        </p>
      </div>

      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onDismiss}>
          Set up later
        </Button>
        <Button type="button" onClick={onContinue}>
          I started the agent
        </Button>
      </DialogFooter>
    </>
  )
}
