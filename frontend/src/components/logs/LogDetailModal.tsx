import { Copy } from 'lucide-react'
import { toast } from 'sonner'
import type { LogEvent } from '../../api/logs'
import { copyText } from '../../lib/clipboard'
import { formatDateTime } from '../../lib/datetime'
import { Button } from '../ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog'
import { LogLevelBadge } from './LogLevelBadge'

interface LogDetailModalProps {
  log: LogEvent | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

interface ContextView {
  pretty: string | null
  raw: string | null
}

/**
 * Log context is a JSON string, but it is untrusted input and may be malformed.
 * Parsing is done defensively; when it fails the raw string is shown instead.
 */
function parseContext(context: string | null | undefined): ContextView {
  if (!context || context.trim() === '') {
    return { pretty: null, raw: null }
  }
  try {
    const parsed: unknown = JSON.parse(context)
    return { pretty: JSON.stringify(parsed, null, 2), raw: null }
  } catch {
    return { pretty: null, raw: context }
  }
}

function toPayload(log: LogEvent): string {
  return JSON.stringify(
    {
      id: log.id,
      applicationId: log.applicationId,
      timestamp: log.timestamp,
      level: log.level,
      message: log.message,
      context: log.context ?? null,
    },
    null,
    2,
  )
}

export function LogDetailModal({ log, open, onOpenChange }: LogDetailModalProps) {
  if (!log) {
    return null
  }

  const context = parseContext(log.context)

  const handleCopy = async () => {
    const copied = await copyText(toPayload(log))
    if (copied) {
      toast.success('Log copied to clipboard.')
    } else {
      toast.error('Clipboard is unavailable. Select the text and copy it manually.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <LogLevelBadge level={log.level} />
            <span>Log event</span>
          </DialogTitle>
          <DialogDescription>
            <time dateTime={log.timestamp}>{formatDateTime(log.timestamp)}</time>
            <span className="mono ml-2 text-xs text-slate-500">({log.timestamp})</span>
          </DialogDescription>
        </DialogHeader>

        <dl className="mt-4 space-y-4 text-sm">
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Application ID</dt>
            <dd className="mono mt-1 text-slate-300">{log.applicationId}</dd>
          </div>

          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Message</dt>
            <dd className="mt-1 break-words whitespace-pre-wrap text-slate-200">{log.message}</dd>
          </div>

          {context.pretty || context.raw ? (
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Context</dt>
              <dd className="mt-1">
                {context.pretty ? (
                  <pre className="mono max-h-72 overflow-auto rounded-lg border border-white/10 bg-black/30 p-3 text-xs break-words whitespace-pre-wrap text-slate-300">
                    {context.pretty}
                  </pre>
                ) : (
                  <p className="mono break-words whitespace-pre-wrap text-slate-300">{context.raw}</p>
                )}
              </dd>
            </div>
          ) : null}
        </dl>

        <DialogFooter>
          <Button type="button" variant="secondary" onClick={handleCopy}>
            <Copy className="h-4 w-4" aria-hidden="true" />
            Copy log
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
