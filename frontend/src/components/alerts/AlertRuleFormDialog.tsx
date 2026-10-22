import { useEffect, useRef, useState } from 'react'
import { Bell, Clock, History, Plus, X } from 'lucide-react'
import { toast } from 'sonner'
import type {
  AlertConditionSpec,
  AlertRule,
  CreateAlertRuleRequest,
  LogCountPreviewRequest,
} from '../../api/alerts'
import {
  useCreateAlertRuleMutation,
  useLogCountPreviewQuery,
  useUpdateAlertRuleMutation,
} from '../../api/alerts'
import { ApiError } from '../../api/client'
import { useHealthChecksListQuery } from '../../api/healthchecks'
import { useCanManage } from '../../features/organizations/useCanManage'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { RevisionHistoryDialog } from '../revisions/RevisionHistoryDialog'
import { MetricNameSelect } from '../filters/MetricNameSelect'
import {
  ALERT_CONDITIONS,
  ALERT_RULE_TYPES,
  DEFAULT_CONDITION,
  DEFAULT_COUNT_THRESHOLD,
  DEFAULT_DURATION_MINUTES,
  DEFAULT_LOG_COMPARISON,
  DEFAULT_LOGIC_OPERATOR,
  DEFAULT_RULE_TYPE,
  DEFAULT_THRESHOLD,
  DEFAULT_WINDOW_MINUTES,
  LOG_ALERT_CONDITIONS,
  LOG_LEVELS,
  LOG_QUERY_MAX_LENGTH,
  MAX_CONDITIONS,
  MAX_WINDOW_MINUTES,
  MIN_WINDOW_MINUTES,
  alertRuleFormSchema,
  type AlertConditionValue,
  type AlertRuleTypeValue,
  type LogAlertConditionValue,
  type LogLevelValue,
  type LogicOperatorValue,
} from '../../features/alerts/schemas'
import {
  alertConditionLabel,
  alertConditionSymbol,
  alertRuleTypeLabel,
  logicOperatorLabel,
} from '../../features/alerts/labels'
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

interface ConditionDraft {
  id: number
  target: string
  condition: AlertConditionValue
  threshold: string
}

function newCondition(overrides: Partial<Omit<ConditionDraft, 'id'>> = {}, id = 0): ConditionDraft {
  return {
    id,
    target: '',
    condition: DEFAULT_CONDITION,
    threshold: String(DEFAULT_THRESHOLD),
    ...overrides,
  }
}

export function AlertRuleFormDialog({ open, onOpenChange, rule }: AlertRuleFormDialogProps) {
  const isEditing = Boolean(rule)
  const canManage = useCanManage()
  const createMutation = useCreateAlertRuleMutation()
  const updateMutation = useUpdateAlertRuleMutation()
  const healthChecksQuery = useHealthChecksListQuery()
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const nextConditionId = useRef(1)

  const [name, setName] = useState('')
  const [type, setType] = useState<AlertRuleTypeValue>(DEFAULT_RULE_TYPE)
  const [applicationId, setApplicationId] = useState<number | null>(null)
  const [conditions, setConditions] = useState<ConditionDraft[]>([newCondition()])
  const [logicOperator, setLogicOperator] = useState<LogicOperatorValue>(DEFAULT_LOGIC_OPERATOR)
  const [durationMinutes, setDurationMinutes] = useState(String(DEFAULT_DURATION_MINUTES))
  const [logLevel, setLogLevel] = useState<LogLevelValue | null>(null)
  const [logQuery, setLogQuery] = useState('')
  const [windowMinutes, setWindowMinutes] = useState(String(DEFAULT_WINDOW_MINUTES))
  const [comparison, setComparison] = useState<LogAlertConditionValue>(DEFAULT_LOG_COMPARISON)
  const [countThreshold, setCountThreshold] = useState(String(DEFAULT_COUNT_THRESHOLD))
  const [previewFilters, setPreviewFilters] = useState<LogCountPreviewRequest | null>(null)
  const [isActive, setIsActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [historyOpen, setHistoryOpen] = useState(false)

  const isLog = type === 'LOG'
  const previewQuery = useLogCountPreviewQuery(previewFilters)

  useEffect(() => {
    if (!open) {
      return
    }
    const drafts = rule ? toConditionDrafts(rule) : [newCondition()]
    setError(null)
    setName(rule?.name ?? '')
    setType(rule?.type ?? DEFAULT_RULE_TYPE)
    setApplicationId(rule?.applicationId ?? null)
    setConditions(drafts)
    setLogicOperator(rule?.logicOperator ?? DEFAULT_LOGIC_OPERATOR)
    setDurationMinutes(rule ? String(rule.durationMinutes) : String(DEFAULT_DURATION_MINUTES))
    setLogLevel(rule?.logLevel ?? null)
    setLogQuery(rule?.logQuery ?? '')
    setWindowMinutes(String(rule?.windowMinutes ?? DEFAULT_WINDOW_MINUTES))
    setComparison(rule?.comparison ?? DEFAULT_LOG_COMPARISON)
    setCountThreshold(String(rule?.countThreshold ?? DEFAULT_COUNT_THRESHOLD))
    setPreviewFilters(null)
    setIsActive(rule?.isActive ?? true)
    nextConditionId.current = drafts.length
  }, [open, rule])

  // Calibration is a network request: wait for typing to settle, and never preview an incomplete
  // filter (the backend rejects an empty level+query pair).
  useEffect(() => {
    if (!open || type !== 'LOG' || applicationId === null) {
      setPreviewFilters(null)
      return
    }
    const windowSize = Number(windowMinutes)
    if (!Number.isInteger(windowSize) || windowSize < MIN_WINDOW_MINUTES || windowSize > MAX_WINDOW_MINUTES) {
      setPreviewFilters(null)
      return
    }
    const query = logQuery.trim()
    if (logLevel === null && query === '') {
      setPreviewFilters(null)
      return
    }
    const timer = window.setTimeout(() => {
      setPreviewFilters({
        applicationId,
        logLevel,
        logQuery: query === '' ? null : query,
        windowMinutes: windowSize,
      })
    }, 500)
    return () => window.clearTimeout(timer)
  }, [open, type, applicationId, logLevel, logQuery, windowMinutes])

  const handleTypeChange = (next: AlertRuleTypeValue) => {
    if (next === type) {
      return
    }
    // Rule kinds use different target scopes, so reset the type-specific state.
    setType(next)
    nextConditionId.current = 1
    setPreviewFilters(null)
    if (next === 'HEALTH_CHECK') {
      setApplicationId(null)
      setConditions([newCondition({ target: '', condition: 'EQUAL', threshold: '0' }, 0)])
    } else if (next === 'LOG') {
      setConditions([])
    } else {
      setConditions([newCondition({ target: '', condition: DEFAULT_CONDITION, threshold: String(DEFAULT_THRESHOLD) }, 0)])
    }
    setLogicOperator(DEFAULT_LOGIC_OPERATOR)
  }

  const addCondition = () => {
    setConditions((current) =>
      current.length >= MAX_CONDITIONS
        ? current
        : [...current, newCondition({ target: '', condition: DEFAULT_CONDITION, threshold: String(DEFAULT_THRESHOLD) }, nextConditionId.current++)],
    )
  }

  const removeCondition = (id: number) => {
    setConditions((current) => (current.length <= 1 ? current : current.filter((condition) => condition.id !== id)))
  }

  const updateCondition = (id: number, patch: Partial<Omit<ConditionDraft, 'id'>>) => {
    setConditions((current) => current.map((condition) => (condition.id === id ? { ...condition, ...patch } : condition)))
  }

  const handleSubmit = async () => {
    const parsed = alertRuleFormSchema.safeParse({
      name,
      type,
      applicationId,
      conditions,
      logicOperator,
      durationMinutes,
      logLevel,
      logQuery,
      windowMinutes,
      comparison,
      countThreshold,
      isActive,
    })

    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
      return
    }

    const values = parsed.data
    const isLogRule = values.type === 'LOG'
    const specs: AlertConditionSpec[] = isLogRule
      ? []
      : values.type === 'HEALTH_CHECK'
        ? values.conditions.map((condition) => ({ target: condition.target, operator: 'EQUAL', threshold: 0 }))
        : values.conditions.map((condition) => ({
            target: condition.target,
            operator: condition.operator,
            threshold: condition.threshold,
          }))
    const first = specs[0]

    const payload: CreateAlertRuleRequest = {
      name: values.name,
      applicationId: values.type === 'HEALTH_CHECK' ? null : values.applicationId,
      type: values.type,
      // The backend derives evaluation from `conditions`; the legacy fields mirror the first one.
      // Log rules use the legacy columns for their count comparison and threshold.
      target: isLogRule ? 'logs' : (first?.target ?? ''),
      condition: isLogRule ? values.comparison : (first?.operator ?? DEFAULT_CONDITION),
      threshold: isLogRule ? values.countThreshold : (first?.threshold ?? DEFAULT_THRESHOLD),
      conditions: specs,
      logicOperator: specs.length > 1 ? values.logicOperator : 'AND',
      logLevel: isLogRule ? values.logLevel : null,
      logQuery: isLogRule ? (values.logQuery === '' ? null : values.logQuery) : null,
      windowMinutes: isLogRule ? values.windowMinutes : null,
      comparison: isLogRule ? values.comparison : null,
      countThreshold: isLogRule ? values.countThreshold : null,
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
  const isMetric = type === 'METRIC_THRESHOLD'
  const canRemove = isMetric && conditions.length > 1

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit alert rule' : 'Create alert rule'}</DialogTitle>
          <DialogDescription>
            The platform evaluates active rules on a schedule and opens an incident when the condition holds. Metric
            rules can combine multiple conditions with AND or OR; log rules count matching events in a time window.
          </DialogDescription>
          {isEditing && rule ? (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="mt-2 w-fit"
              onClick={() => setHistoryOpen(true)}
            >
              <History className="h-4 w-4" aria-hidden="true" />
              History
            </Button>
          ) : null}
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

          {isMetric || isLog ? (
            <div className="space-y-1.5">
              <Label htmlFor="alert-application">Application</Label>
              <ApplicationSelect id="alert-application" value={applicationId} onChange={setApplicationId} />
            </div>
          ) : null}

          {!isLog ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <Label>{isMetric ? 'Conditions' : 'Health check'}</Label>
              {isMetric && conditions.length > 1 ? (
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-500">Match</span>
                  <Select
                    value={logicOperator}
                    onValueChange={(value) => setLogicOperator(value as LogicOperatorValue)}
                  >
                    <SelectTrigger id="alert-logic" className="h-8 w-24" aria-label="Condition logic">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {(['AND', 'OR'] as const).map((value) => (
                        <SelectItem key={value} value={value}>
                          {logicOperatorLabel(value)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              ) : null}
            </div>

            {conditions.map((condition, index) => (
              <div
                key={condition.id}
                className="space-y-3 rounded-lg border border-white/10 bg-white/[0.02] p-3"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium uppercase tracking-wide text-slate-500">
                    {isMetric ? `Condition ${index + 1}` : 'Check'}
                  </span>
                  {canRemove ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-slate-400 hover:text-red-300"
                      aria-label={`Remove condition ${index + 1}`}
                      onClick={() => removeCondition(condition.id)}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  ) : null}
                </div>

                {isMetric ? (
                  <>
                    <div className="space-y-1.5">
                      <Label htmlFor={`alert-metric-${index}`}>Metric</Label>
                      <MetricNameSelect
                        id={`alert-metric-${index}`}
                        applicationId={applicationId}
                        value={condition.target}
                        onChange={(next) => updateCondition(condition.id, { target: next })}
                      />
                    </div>
                    <div className="grid gap-4 sm:grid-cols-2">
                      <div className="space-y-1.5">
                        <Label htmlFor={`alert-condition-${index}`}>Condition</Label>
                        <Select
                          value={condition.condition}
                          onValueChange={(value) =>
                            updateCondition(condition.id, { condition: value as AlertConditionValue })
                          }
                        >
                          <SelectTrigger id={`alert-condition-${index}`}>
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
                        <Label htmlFor={`alert-threshold-${index}`}>Threshold</Label>
                        <Input
                          id={`alert-threshold-${index}`}
                          type="number"
                          inputMode="decimal"
                          step="any"
                          value={condition.threshold}
                          onChange={(event) => updateCondition(condition.id, { threshold: event.target.value })}
                        />
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="space-y-1.5">
                    <Select
                      value={condition.target}
                      onValueChange={(next) => updateCondition(condition.id, { target: next })}
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
              </div>
            ))}

            {isMetric ? (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={addCondition}
                disabled={conditions.length >= MAX_CONDITIONS}
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                Add condition
              </Button>
            ) : null}
          </div>
          ) : null}

          {isLog ? (
            <div className="space-y-3 rounded-lg border border-white/10 bg-white/[0.02] p-3">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="alert-log-level">Level</Label>
                  <Select
                    value={logLevel ?? 'ALL'}
                    onValueChange={(value) =>
                      setLogLevel(value === 'ALL' ? null : (value as LogLevelValue))
                    }
                  >
                    <SelectTrigger id="alert-log-level">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">Any level</SelectItem>
                      {LOG_LEVELS.map((value) => (
                        <SelectItem key={value} value={value}>
                          {value}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="alert-window">Window (minutes)</Label>
                  <Input
                    id="alert-window"
                    type="number"
                    inputMode="numeric"
                    min={MIN_WINDOW_MINUTES}
                    max={MAX_WINDOW_MINUTES}
                    value={windowMinutes}
                    onChange={(event) => setWindowMinutes(event.target.value)}
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="alert-log-query">Message contains</Label>
                <Input
                  id="alert-log-query"
                  autoComplete="off"
                  maxLength={LOG_QUERY_MAX_LENGTH}
                  placeholder="timeout"
                  value={logQuery}
                  onChange={(event) => setLogQuery(event.target.value)}
                />
                <p className="text-xs text-slate-500">
                  Case-insensitive substring match. Choose a level, enter a query, or both.
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="alert-log-comparison">Fire when count is</Label>
                  <Select
                    value={comparison}
                    onValueChange={(value) => setComparison(value as LogAlertConditionValue)}
                  >
                    <SelectTrigger id="alert-log-comparison">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {LOG_ALERT_CONDITIONS.map((value) => (
                        <SelectItem key={value} value={value}>
                          {`${alertConditionSymbol(value)} ${alertConditionLabel(value)}`}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="alert-count-threshold">Count threshold</Label>
                  <Input
                    id="alert-count-threshold"
                    type="number"
                    inputMode="numeric"
                    min={0}
                    step={1}
                    value={countThreshold}
                    onChange={(event) => setCountThreshold(event.target.value)}
                  />
                </div>
              </div>

              <p role="status" aria-live="polite" className="text-xs text-slate-400">
                {applicationId === null ? (
                  'Select an application to preview matching logs.'
                ) : previewFilters === null ? (
                  'Choose a level or enter a query to preview matching logs.'
                ) : previewQuery.isFetching ? (
                  'Counting matching logs…'
                ) : previewQuery.isError ? (
                  <span className="text-red-400">
                    {previewQuery.error instanceof ApiError
                      ? previewQuery.error.message
                      : 'Could not count matching logs.'}
                  </span>
                ) : previewQuery.data ? (
                  <>
                    In the last {previewQuery.data.windowMinutes} minutes this query matched{' '}
                    <strong className="text-slate-200">{previewQuery.data.count.toLocaleString()}</strong> logs.
                  </>
                ) : (
                  'No preview available.'
                )}
              </p>
            </div>
          ) : null}

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

      {rule ? (
        <RevisionHistoryDialog
          open={historyOpen}
          onOpenChange={setHistoryOpen}
          kind="alert-rule"
          resourceId={rule.id}
          resourceName={rule.name}
          canRestore={canManage}
          onRestored={() => {
            setHistoryOpen(false)
            onOpenChange(false)
          }}
        />
      ) : null}
    </Dialog>
  )
}

function toConditionDrafts(rule: AlertRule): ConditionDraft[] {
  const specs: AlertConditionSpec[] =
    rule.conditions && rule.conditions.length > 0
      ? rule.conditions
      : [{ target: rule.target, operator: rule.condition, threshold: rule.threshold }]
  return specs.map((spec, index) => ({
    id: index,
    target: spec.target,
    condition: spec.operator,
    threshold: String(spec.threshold),
  }))
}
