import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import type { Application } from '../../api/applications'
import type { LogPipelineTestResponse } from '../../api/logPipelines'
import { useTestLogPipelineMutation } from '../../api/logPipelines'
import type { LogLevel } from '../../api/logs'
import { LOG_LEVELS } from '../../features/logs/schemas'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface LogPipelineTestDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  applications: Application[]
  defaultApplicationId: number | null
}

export function LogPipelineTestDialog({
  open,
  onOpenChange,
  applications,
  defaultApplicationId,
}: LogPipelineTestDialogProps) {
  const mutation = useTestLogPipelineMutation()
  const [applicationId, setApplicationId] = useState('')
  const [level, setLevel] = useState<LogLevel>('INFO')
  const [message, setMessage] = useState('order 12345 failed')
  const [context, setContext] = useState('{"service":"checkout"}')
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<LogPipelineTestResponse | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    setError(null)
    setResult(null)
    const preferred = defaultApplicationId ?? applications[0]?.id
    setApplicationId(preferred === undefined ? '' : String(preferred))
  }, [open, defaultApplicationId, applications])

  const handleRun = async () => {
    if (!applicationId) {
      setError('Select an application.')
      return
    }
    if (!message.trim()) {
      setError('Message is required.')
      return
    }
    setError(null)
    try {
      const response = await mutation.mutateAsync({
        applicationId: Number(applicationId),
        level,
        message,
        context: context.trim() === '' ? undefined : context,
      })
      setResult(response)
    } catch (testError) {
      setResult(null)
      setError(testError instanceof ApiError ? testError.message : 'Could not run the pipeline.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Test pipeline rules</DialogTitle>
          <DialogDescription>
            Runs the saved rules for this application against a sample event. Nothing is stored.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-6 space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="pipeline-test-application">Application</Label>
              <Select value={applicationId} onValueChange={setApplicationId}>
                <SelectTrigger id="pipeline-test-application">
                  <SelectValue placeholder="Select application" />
                </SelectTrigger>
                <SelectContent>
                  {applications.map((application) => (
                    <SelectItem key={application.id} value={String(application.id)}>
                      {application.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="pipeline-test-level">Level</Label>
              <Select value={level} onValueChange={(value) => setLevel(value as LogLevel)}>
                <SelectTrigger id="pipeline-test-level">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {LOG_LEVELS.map((value) => (
                    <SelectItem key={value} value={value}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="pipeline-test-message">Message</Label>
            <Input
              id="pipeline-test-message"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="pipeline-test-context">Context JSON</Label>
            <textarea
              id="pipeline-test-context"
              value={context}
              onChange={(event) => setContext(event.target.value)}
              spellCheck={false}
              className="flex min-h-24 w-full rounded-md border border-white/10 bg-canvas-2 px-3 py-2 font-mono text-sm text-slate-100 placeholder:text-slate-500 focus-visible:border-cyan-400/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50"
            />
          </div>

          {error ? (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          ) : null}

          {result ? <TestResult result={result} /> : null}
        </div>

        <DialogFooter>
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button type="button" onClick={() => void handleRun()} disabled={mutation.isPending}>
            {mutation.isPending ? 'Running…' : 'Run test'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function TestResult({ result }: { result: LogPipelineTestResponse }) {
  return (
    <div className="space-y-3 rounded-xl border border-white/10 bg-canvas-2/60 p-4" role="status">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={result.dropped ? 'danger' : 'success'}>
          {result.dropped ? 'Dropped' : 'Kept'}
        </Badge>
        <span className="text-xs text-slate-400">
          {result.matchedRules.length} matched {result.matchedRules.length === 1 ? 'rule' : 'rules'}
        </span>
      </div>

      {result.matchedRules.length > 0 ? (
        <ul className="flex flex-wrap gap-1.5">
          {result.matchedRules.map((rule) => (
            <li key={rule.id}>
              <Badge variant="muted">
                {rule.name} · {rule.action}
              </Badge>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-xs text-slate-500">No rules matched this event.</p>
      )}

      {result.output ? (
        <div className="space-y-2">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Message</p>
            <pre className="mt-1 overflow-x-auto whitespace-pre-wrap break-words rounded-md bg-black/20 p-2 text-xs text-slate-200">
              {result.output.message}
            </pre>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Context</p>
            <pre className="mt-1 overflow-x-auto whitespace-pre-wrap break-all rounded-md bg-black/20 p-2 text-xs text-slate-200">
              {result.output.context ?? ''}
            </pre>
          </div>
        </div>
      ) : null}
    </div>
  )
}
