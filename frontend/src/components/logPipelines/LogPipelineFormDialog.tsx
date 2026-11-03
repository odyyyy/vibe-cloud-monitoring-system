import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { Application } from '../../api/applications'
import type { LogPipelineAction, LogPipelineRule } from '../../api/logPipelines'
import {
  LOG_PIPELINE_ACTIONS,
  useCreateLogPipelineRuleMutation,
  useUpdateLogPipelineRuleMutation,
} from '../../api/logPipelines'
import {
  pipelineActionLabel,
  pipelineRuleFormSchema,
  toPipelineRuleRequest,
} from '../../features/logPipelines/schemas'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface LogPipelineFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  rule: LogPipelineRule | null
  applications: Application[]
}

interface FormState {
  name: string
  applicationId: string
  filter: string
  action: LogPipelineAction
  pattern: string
  target: 'MESSAGE' | 'CONTEXT'
  field: string
  key: string
  value: string
}

const EMPTY_FORM: FormState = {
  name: '',
  applicationId: 'all',
  filter: '*',
  action: 'PARSE_REGEX',
  pattern: '',
  target: 'MESSAGE',
  field: '',
  key: '',
  value: '',
}

export function LogPipelineFormDialog({ open, onOpenChange, rule, applications }: LogPipelineFormDialogProps) {
  const createMutation = useCreateLogPipelineRuleMutation()
  const updateMutation = useUpdateLogPipelineRuleMutation()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    setError(null)
    if (!rule) {
      setForm(EMPTY_FORM)
      return
    }
    const config = rule.actionConfig ?? {}
    setForm({
      name: rule.name,
      applicationId: rule.applicationId === null ? 'all' : String(rule.applicationId),
      filter: rule.filter,
      action: rule.action,
      pattern: typeof config.pattern === 'string' ? config.pattern : '',
      target:
        typeof config.target === 'string' && config.target.toUpperCase() === 'CONTEXT'
          ? 'CONTEXT'
          : 'MESSAGE',
      field: typeof config.field === 'string' ? config.field : '',
      key: typeof config.key === 'string' ? config.key : '',
      value: typeof config.value === 'string' ? config.value : '',
    })
  }, [open, rule])

  const patch = (next: Partial<FormState>) => {
    setForm((current) => ({ ...current, ...next }))
  }

  const handleSubmit = async () => {
    const parsed = pipelineRuleFormSchema.safeParse(form)
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
      return
    }

    setError(null)
    const request = toPipelineRuleRequest(parsed.data)
    try {
      if (rule) {
        await updateMutation.mutateAsync({ id: rule.id, data: request })
        toast.success('Pipeline rule updated.')
      } else {
        await createMutation.mutateAsync(request)
        toast.success('Pipeline rule created.')
      }
      onOpenChange(false)
    } catch (submitError) {
      setError(submitError instanceof ApiError ? submitError.message : 'Could not save the pipeline rule.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{rule ? 'Edit pipeline rule' : 'Create pipeline rule'}</DialogTitle>
          <DialogDescription>
            Rules run in order before logs are buffered. The same rule set is applied again by the backend.
          </DialogDescription>
        </DialogHeader>

        <form
          className="mt-6 space-y-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void handleSubmit()
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="pipeline-name">Name</Label>
            <Input
              id="pipeline-name"
              autoFocus
              autoComplete="off"
              value={form.name}
              onChange={(event) => patch({ name: event.target.value })}
              placeholder="Drop checkout health checks"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="pipeline-application">Scope</Label>
            <Select
              value={form.applicationId}
              onValueChange={(value) => patch({ applicationId: value })}
            >
              <SelectTrigger id="pipeline-application">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All applications</SelectItem>
                {applications.map((application) => (
                  <SelectItem key={application.id} value={String(application.id)}>
                    {application.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="pipeline-filter">Filter</Label>
            <Input
              id="pipeline-filter"
              value={form.filter}
              onChange={(event) => patch({ filter: event.target.value })}
              placeholder='level:DEBUG&&message:"healthcheck"'
              spellCheck={false}
            />
            <p className="text-xs text-slate-500">
              AND clauses: level, message, host, service, context.&lt;key&gt;. Use * to match everything.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="pipeline-action">Action</Label>
            <Select
              value={form.action}
              onValueChange={(value) => patch({ action: value as LogPipelineAction })}
            >
              <SelectTrigger id="pipeline-action">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {LOG_PIPELINE_ACTIONS.map((action) => (
                  <SelectItem key={action} value={action}>
                    {pipelineActionLabel(action)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {form.action === 'PARSE_REGEX' || form.action === 'MASK' ? (
            <div className="space-y-1.5">
              <Label htmlFor="pipeline-pattern">Pattern</Label>
              <Input
                id="pipeline-pattern"
                value={form.pattern}
                onChange={(event) => patch({ pattern: event.target.value })}
                placeholder={form.action === 'PARSE_REGEX' ? 'order (?P<order_id>\\d+)' : '\\d{4}-\\d{4}'}
                spellCheck={false}
                className="font-mono"
              />
              <p className="text-xs text-slate-500">
                RE2-compatible. Named groups such as (?P&lt;name&gt;...) become context fields.
              </p>
            </div>
          ) : null}

          {form.action === 'ADD_ATTRIBUTE' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="pipeline-key">Attribute key</Label>
                <Input
                  id="pipeline-key"
                  value={form.key}
                  onChange={(event) => patch({ key: event.target.value })}
                  placeholder="environment"
                  spellCheck={false}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="pipeline-value">Value</Label>
                <Input
                  id="pipeline-value"
                  value={form.value}
                  onChange={(event) => patch({ value: event.target.value })}
                  placeholder="production"
                />
              </div>
            </div>
          ) : null}

          {form.action === 'MASK' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="pipeline-target">Mask target</Label>
                <Select
                  value={form.target}
                  onValueChange={(value) => patch({ target: value as 'MESSAGE' | 'CONTEXT' })}
                >
                  <SelectTrigger id="pipeline-target">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="MESSAGE">Message</SelectItem>
                    <SelectItem value="CONTEXT">Context field</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {form.target === 'CONTEXT' ? (
                <div className="space-y-1.5">
                  <Label htmlFor="pipeline-field">Context field</Label>
                  <Input
                    id="pipeline-field"
                    value={form.field}
                    onChange={(event) => patch({ field: event.target.value })}
                    placeholder="card_number"
                    spellCheck={false}
                  />
                </div>
              ) : null}
            </div>
          ) : null}

          {error ? (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : rule ? 'Save changes' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
