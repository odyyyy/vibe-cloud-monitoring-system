import { useEffect, useState } from 'react'
import { Wrench } from 'lucide-react'
import { toast } from 'sonner'
import { useAlertRulesQuery } from '../../api/alerts'
import { ApiError } from '../../api/client'
import type {
  CreateMaintenanceWindowRequest,
  MaintenanceScopeType,
  MaintenanceWindow,
  UpdateMaintenanceWindowRequest,
} from '../../api/maintenanceWindows'
import {
  useCreateMaintenanceWindowMutation,
  useUpdateMaintenanceWindowMutation,
} from '../../api/maintenanceWindows'
import { FormField } from '../auth/FormField'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import { Switch } from '../ui/switch'
import { parseUtcInstant } from '../../lib/datetime'

interface MaintenanceWindowFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  window: MaintenanceWindow | null
}

const SCOPE_OPTIONS: { value: MaintenanceScopeType; label: string }[] = [
  { value: 'GLOBAL', label: 'All alerts' },
  { value: 'APPLICATION', label: 'One application' },
  { value: 'SPECIFIC_RULE', label: 'One alert rule' },
]

const MAX_BACKDATING_MS = 60 * 60 * 1000

const pad = (value: number) => String(value).padStart(2, '0')

/** Formats a UTC instant for a `datetime-local` input in the browser's local time. */
function toLocalInput(value: string): string {
  const date = parseUtcInstant(value)
  if (!date) {
    return ''
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function defaultTimes(): { start: string; end: string } {
  const start = new Date()
  start.setSeconds(0, 0)
  start.setMinutes(start.getMinutes() + 5)
  const end = new Date(start.getTime() + 2 * 60 * 60 * 1000)
  return { start: toLocalInput(start.toISOString()), end: toLocalInput(end.toISOString()) }
}

export function MaintenanceWindowFormDialog({
  open,
  onOpenChange,
  window,
}: MaintenanceWindowFormDialogProps) {
  const isEditing = Boolean(window)
  const createMutation = useCreateMaintenanceWindowMutation()
  const updateMutation = useUpdateMaintenanceWindowMutation()
  const rulesQuery = useAlertRulesQuery()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const [name, setName] = useState('')
  const [scopeType, setScopeType] = useState<MaintenanceScopeType>('GLOBAL')
  const [scopeId, setScopeId] = useState<number | null>(null)
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [initialStart, setInitialStart] = useState('')
  const [initialEnd, setInitialEnd] = useState('')
  const [isActive, setIsActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    const defaults = defaultTimes()
    const nextStart = window ? toLocalInput(window.startTime) : defaults.start
    const nextEnd = window ? toLocalInput(window.endTime) : defaults.end
    setError(null)
    setName(window?.name ?? '')
    setScopeType(window?.scopeType ?? 'GLOBAL')
    setScopeId(window?.scopeId ?? null)
    setStart(nextStart)
    setEnd(nextEnd)
    setInitialStart(nextStart)
    setInitialEnd(nextEnd)
    setIsActive(window?.isActive ?? true)
  }, [open, window])

  const handleScopeChange = (next: MaintenanceScopeType) => {
    setScopeType(next)
    setScopeId(null)
    setError(null)
  }

  const handleSubmit = async () => {
    const trimmedName = name.trim()
    if (trimmedName === '') {
      setError('Name is required.')
      return
    }
    if (trimmedName.length > 100) {
      setError('Name must be at most 100 characters.')
      return
    }
    if (scopeType !== 'GLOBAL' && scopeId === null) {
      setError('Select the application or alert rule this window should mute.')
      return
    }

    const startDate = new Date(start)
    const endDate = new Date(end)
    if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
      setError('Enter a valid start and end time.')
      return
    }

    const startChanged = !isEditing || start !== initialStart
    const endChanged = !isEditing || end !== initialEnd
    const now = Date.now()
    if (startChanged && startDate.getTime() < now - MAX_BACKDATING_MS) {
      setError('Start time cannot be more than 1 hour in the past.')
      return
    }
    if (endChanged && endDate.getTime() <= now) {
      setError('End time must be in the future.')
      return
    }
    if (endDate.getTime() <= startDate.getTime()) {
      setError('End time must be after the start time.')
      return
    }

    const resolvedScopeId = scopeType === 'GLOBAL' ? null : scopeId
    setError(null)

    try {
      if (window) {
        const payload: UpdateMaintenanceWindowRequest = {
          name: trimmedName,
          scopeType,
          scopeId: resolvedScopeId,
          isActive,
        }
        if (startChanged) {
          payload.startTime = startDate.toISOString()
        }
        if (endChanged) {
          payload.endTime = endDate.toISOString()
        }
        await updateMutation.mutateAsync({ id: window.id, data: payload })
        toast.success('Maintenance window updated.')
      } else {
        const payload: CreateMaintenanceWindowRequest = {
          name: trimmedName,
          scopeType,
          scopeId: resolvedScopeId,
          startTime: startDate.toISOString(),
          endTime: endDate.toISOString(),
          isActive,
        }
        await createMutation.mutateAsync(payload)
        toast.success('Maintenance window created.')
      }
      onOpenChange(false)
    } catch (submitError) {
      setError(
        submitError instanceof ApiError
          ? submitError.message
          : 'Could not save the maintenance window.',
      )
    }
  }

  const rules = rulesQuery.data ?? []

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? 'Edit maintenance window' : 'Create maintenance window'}
          </DialogTitle>
          <DialogDescription>
            While a window is active, matching alerts do not open new incidents or send
            notifications. Existing incidents are still resolved when the condition recovers.
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
          <FormField
            label="Name"
            icon={Wrench}
            autoFocus
            autoComplete="off"
            placeholder="Database upgrade"
            maxLength={100}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />

          <div className="space-y-1.5">
            <Label htmlFor="maintenance-scope">Scope</Label>
            <Select
              value={scopeType}
              onValueChange={(value) => handleScopeChange(value as MaintenanceScopeType)}
            >
              <SelectTrigger id="maintenance-scope">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SCOPE_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {scopeType === 'APPLICATION' ? (
            <div className="space-y-1.5">
              <Label htmlFor="maintenance-application">Application</Label>
              <ApplicationSelect
                id="maintenance-application"
                value={scopeId}
                onChange={setScopeId}
              />
            </div>
          ) : null}

          {scopeType === 'SPECIFIC_RULE' ? (
            <div className="space-y-1.5">
              <Label htmlFor="maintenance-rule">Alert rule</Label>
              <Select
                value={scopeId === null ? '' : String(scopeId)}
                onValueChange={(value) => setScopeId(value === '' ? null : Number(value))}
                disabled={rulesQuery.isLoading || rulesQuery.isError || rules.length === 0}
              >
                <SelectTrigger id="maintenance-rule">
                  <SelectValue
                    placeholder={
                      rulesQuery.isLoading
                        ? 'Loading alert rules…'
                        : rulesQuery.isError
                          ? 'Could not load alert rules'
                          : rules.length === 0
                            ? 'No alert rules available'
                            : 'Select an alert rule'
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {rules.map((rule) => (
                    <SelectItem key={rule.id} value={String(rule.id)}>
                      {rule.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="maintenance-start">Start</Label>
              <Input
                id="maintenance-start"
                type="datetime-local"
                step={60}
                value={start}
                onChange={(event) => setStart(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="maintenance-end">End</Label>
              <Input
                id="maintenance-end"
                type="datetime-local"
                step={60}
                value={end}
                onChange={(event) => setEnd(event.target.value)}
              />
            </div>
          </div>
          <p className="text-xs text-slate-500">
            Times are shown in your local timezone. Start time may be up to 1 hour in the past; the
            end must be in the future.
          </p>

          <div className="flex items-center justify-between rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5">
            <Label htmlFor="maintenance-active" className="text-sm text-slate-200">
              Enabled
            </Label>
            <Switch id="maintenance-active" checked={isActive} onCheckedChange={setIsActive} />
          </div>

          {error ? (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          ) : null}

          <DialogFooter>
            <Button
              type="button"
              variant="secondary"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
