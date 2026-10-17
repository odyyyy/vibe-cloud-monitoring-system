import { useEffect, useState } from 'react'
import { Bell, Clock } from 'lucide-react'
import { toast } from 'sonner'
import type { AlertRule, CreateAlertRuleRequest } from '../../api/alerts'
import { useCreateAlertRuleMutation, useUpdateAlertRuleMutation } from '../../api/alerts'
import { ApiError } from '../../api/client'
import { useHealthChecksListQuery } from '../../api/healthchecks'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { MetricNameSelect } from '../filters/MetricNameSelect'
import {
  ALERT_CONDITIONS,
  ALERT_RULE_TYPES,
  DEFAULT_CONDITION,
  DEFAULT_DURATION_MINUTES,
  DEFAULT_RULE_TYPE,
  DEFAULT_THRESHOLD,
  alertRuleFormSchema,
  type AlertConditionValue,
  type AlertRuleTypeValue,
} from '../../features/alerts/schemas'
import { alertConditionLabel, alertConditionSymbol, alertRuleTypeLabel } from '../../features/alerts/labels'
import { FormField } from '../auth/FormField'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import { Switch } from '../ui/switch'

interface AlertRuleFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  rule: AlertRule | null
}

export function AlertRuleFormDialog({ open, onOpenChange, rule }: AlertRuleFormDialogProps) {
  const isEditing = Boolean(rule)
  const createMutation = useCreateAlertRuleMutation()
  const updateMutation = useUpdateAlertRuleMutation()
  const healthChecksQuery = useHealthChecksListQuery()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const [name, setName] = useState('')
  const [type, setType] = useState<AlertRuleTypeValue>(DEFAULT_RULE_TYPE)
  const [applicationId, setApplicationId] = useState<number | null>(null)
  const [target, setTarget] = useState('')
  const [condition, setCondition] = useState<AlertConditionValue>(DEFAULT_CONDITION)
  const [threshold, setThreshold] = useState(String(DEFAULT_THRESHOLD))
  const [durationMinutes, setDurationMinutes] = useState(String(DEFAULT_DURATION_MINUTES))
  const [isActive, setIsActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    setError(null)
    setName(rule?.name ?? '')
    setType(rule?.type ?? DEFAULT_RULE_TYPE)
    setApplicationId(rule?.applicationId ?? null)
    setTarget(rule?.target ?? '')
    setCondition(rule?.condition ?? DEFAULT_CONDITION)
    setThreshold(rule ? String(rule.threshold) : String(DEFAULT_THRESHOLD))
    setDurationMinutes(rule ? String(rule.durationMinutes) : String(DEFAULT_DURATION_MINUTES))
    setIsActive(rule?.isActive ?? true)
  }, [open, rule])

  const handleTypeChange = (next: AlertRuleTypeValue) => {
    if (next === type) {
      return
    }
    // The two rule kinds use different target scopes, so reset the target and its scope.
    setType(next)
    setTarget('')
    if (next === 'HEALTH_CHECK') {
      setApplicationId(null)
      setCondition('EQUAL')
      setThreshold('0')
    }
  }

  const handleSubmit = async () => {
    const parsed = alertRuleFormSchema.safeParse({
      name,
      type,
      applicationId,
      target,
      condition,
      threshold,
      durationMinutes,
      isActive,
    })

    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
      return
    }

    const values = parsed.data
    const payload: CreateAlertRuleRequest = {
      name: values.name,
      applicationId: values.type === 'METRIC_THRESHOLD' ? values.applicationId : null,
      type: values.type,
      target: values.target,
      condition: values.type === 'HEALTH_CHECK' ? 'EQUAL' : values.condition,
      threshold: values.type === 'HEALTH_CHECK' ? 0 : values.threshold,
      durationMinutes: values.durationMinutes,
      isActive: values.isActive,
    }

    setError(null)
    try {
      if (rule) {
        await updateMutation.mutateAsync({ id: rule.id, data: payload })
        toast.success('Alert rule updated.')
      } else {
        await createMutation.mutateAsync(payload)
        toast.success('Alert rule created.')
      }
      onOpenChange(false)
    } catch (submitError) {
      setError(submitError instanceof ApiError ? submitError.message : 'Could not save the alert rule.')
    }
  }

  const healthChecks = healthChecksQuery.data ?? []

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit alert rule' : 'Create alert rule'}</DialogTitle>
          <DialogDescription>
            The platform evaluates active rules on a schedule and opens an incident when the condition holds.
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
            icon={Bell}
            autoFocus
            autoComplete="off"
            placeholder="High CPU usage"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />

          <div className="space-y-1.5">
            <Label htmlFor="alert-type">Rule type</Label>
            <Select value={type} onValueChange={(value) => handleTypeChange(value as AlertRuleTypeValue)}>
              <SelectTrigger id="alert-type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ALERT_RULE_TYPES.map((value) => (
                  <SelectItem key={value} value={value}>
                    {alertRuleTypeLabel(value)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {type === 'METRIC_THRESHOLD' ? (
            <>
              <div className="space-y-1.5">
                <Label htmlFor="alert-application">Application</Label>
                <ApplicationSelect id="alert-application" value={applicationId} onChange={setApplicationId} />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="alert-metric">Metric</Label>
                <MetricNameSelect applicationId={applicationId} value={target} onChange={setTarget} id="alert-metric" />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="alert-condition">Condition</Label>
                  <Select value={condition} onValueChange={(value) => setCondition(value as AlertConditionValue)}>
                    <SelectTrigger id="alert-condition">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {ALERT_CONDITIONS.map((value) => (
                        <SelectItem key={value} value={value}>
                          {`${alertConditionSymbol(value)} ${alertConditionLabel(value)}`}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="alert-threshold">Threshold</Label>
                  <Input
                    id="alert-threshold"
                    type="number"
                    inputMode="decimal"
                    step="any"
                    value={threshold}
                    onChange={(event) => setThreshold(event.target.value)}
                  />
                </div>
              </div>
            </>
          ) : (
            <div className="space-y-1.5">
              <Label htmlFor="alert-health-check">Health check</Label>
              <Select
                value={target}
                onValueChange={setTarget}
                disabled={healthChecksQuery.isLoading || healthChecksQuery.isError || healthChecks.length === 0}
              >
                <SelectTrigger id="alert-health-check" aria-label="Health check">
                  <SelectValue
                    placeholder={
                      healthChecksQuery.isLoading
                        ? 'Loading health checks…'
                        : healthChecksQuery.isError
                          ? 'Could not load health checks'
                          : healthChecks.length === 0
                            ? 'No health checks available'
                            : 'Select a health check'
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {healthChecks.map((check) => (
                    <SelectItem key={check.id} value={String(check.id)}>
                      {check.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-slate-500">Fires when the check reports Down or Timeout.</p>
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="alert-duration">Duration (minutes)</Label>
            <div className="relative">
              <Clock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" aria-hidden="true" />
              <Input
                id="alert-duration"
                className="pl-10"
                type="number"
                inputMode="numeric"
                min={1}
                max={1440}
                value={durationMinutes}
                onChange={(event) => setDurationMinutes(event.target.value)}
              />
            </div>
            <p className="text-xs text-slate-500">The condition must hold continuously for this long (1-1440).</p>
          </div>

          <div className="flex items-center justify-between rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5">
            <Label htmlFor="alert-active" className="text-sm text-slate-200">
              Active
            </Label>
            <Switch id="alert-active" checked={isActive} onCheckedChange={setIsActive} />
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
