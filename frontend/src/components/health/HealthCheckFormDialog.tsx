import { useEffect, useState } from 'react'
import { Activity, Link2 } from 'lucide-react'
import { toast } from 'sonner'
import type { HealthCheckConfig, HealthCheckMethod } from '../../api/healthchecks'
import { useCreateHealthCheckMutation, useUpdateHealthCheckMutation } from '../../api/healthchecks'
import { ApiError } from '../../api/client'
import {
  DEFAULT_EXPECTED_STATUS_CODE,
  DEFAULT_INTERVAL_SECONDS,
  DEFAULT_METHOD,
  HEALTH_CHECK_METHODS,
  healthCheckFormSchema,
} from '../../features/health/schemas'
import { FormField } from '../auth/FormField'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import { Switch } from '../ui/switch'

interface HealthCheckFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  healthCheck: HealthCheckConfig | null
}

export function HealthCheckFormDialog({ open, onOpenChange, healthCheck }: HealthCheckFormDialogProps) {
  const isEditing = Boolean(healthCheck)
  const createMutation = useCreateHealthCheckMutation()
  const updateMutation = useUpdateHealthCheckMutation()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const [name, setName] = useState('')
  const [targetUrl, setTargetUrl] = useState('')
  const [method, setMethod] = useState<HealthCheckMethod>(DEFAULT_METHOD)
  const [expectedStatusCode, setExpectedStatusCode] = useState(String(DEFAULT_EXPECTED_STATUS_CODE))
  const [intervalSeconds, setIntervalSeconds] = useState(String(DEFAULT_INTERVAL_SECONDS))
  const [isActive, setIsActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    setError(null)
    setName(healthCheck?.name ?? '')
    setTargetUrl(healthCheck?.targetUrl ?? '')
    setMethod(healthCheck?.method ?? DEFAULT_METHOD)
    setExpectedStatusCode(String(healthCheck?.expectedStatusCode ?? DEFAULT_EXPECTED_STATUS_CODE))
    setIntervalSeconds(String(healthCheck?.intervalSeconds ?? DEFAULT_INTERVAL_SECONDS))
    setIsActive(healthCheck?.isActive ?? true)
  }, [open, healthCheck])

  const handleSubmit = async () => {
    const parsed = healthCheckFormSchema.safeParse({
      name,
      targetUrl,
      method,
      expectedStatusCode,
      intervalSeconds,
      isActive,
    })

    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
      return
    }

    const values = parsed.data
    setError(null)
    try {
      if (healthCheck) {
        await updateMutation.mutateAsync({ id: healthCheck.id, data: values })
        toast.success('Health check updated.')
      } else {
        await createMutation.mutateAsync(values)
        toast.success('Health check created.')
      }
      onOpenChange(false)
    } catch (submitError) {
      // Surface the backend message verbatim (for example an SSRF rejection) so the user
      // knows why the target was refused.
      setError(submitError instanceof ApiError ? submitError.message : 'Could not save the health check.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit health check' : 'Create health check'}</DialogTitle>
          <DialogDescription>
            The backend periodically calls this endpoint and records the result.
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
            icon={Activity}
            autoFocus
            autoComplete="off"
            placeholder="Public API health"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />

          <FormField
            label="Target URL"
            icon={Link2}
            autoComplete="off"
            placeholder="https://api.example.com/health"
            value={targetUrl}
            onChange={(event) => setTargetUrl(event.target.value)}
            hint="Internal, loopback, private and link-local addresses are rejected by the backend (SSRF protection)."
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="health-method">Method</Label>
              <Select value={method} onValueChange={(value) => setMethod(value as HealthCheckMethod)}>
                <SelectTrigger id="health-method">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {HEALTH_CHECK_METHODS.map((value) => (
                    <SelectItem key={value} value={value}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="health-status-code">Expected status code</Label>
              <Input
                id="health-status-code"
                type="number"
                inputMode="numeric"
                min={100}
                max={599}
                value={expectedStatusCode}
                onChange={(event) => setExpectedStatusCode(event.target.value)}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="health-interval">Interval (seconds)</Label>
            <Input
              id="health-interval"
              type="number"
              inputMode="numeric"
              min={10}
              value={intervalSeconds}
              onChange={(event) => setIntervalSeconds(event.target.value)}
            />
            <p className="text-xs text-slate-500">Minimum 10 seconds.</p>
          </div>

          <div className="flex items-center justify-between rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5">
            <Label htmlFor="health-active" className="text-sm text-slate-200">
              Active
            </Label>
            <Switch id="health-active" checked={isActive} onCheckedChange={setIsActive} />
          </div>

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
              {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
