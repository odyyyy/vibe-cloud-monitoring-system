import type { RetentionPolicy, UpdateRetentionPolicyInput } from '../../api/retention'

export interface PolicyFormState {
  logs: string
  metrics: string
  traces: string
}

export function formFromPolicy(policy: RetentionPolicy): PolicyFormState {
  return {
    logs: String(policy.logsRetentionDays),
    metrics: String(policy.metricsRetentionDays),
    traces: String(policy.tracesRetentionDays),
  }
}

export function isPolicyDirty(form: PolicyFormState, policy: RetentionPolicy): boolean {
  return (
    form.logs !== String(policy.logsRetentionDays) ||
    form.metrics !== String(policy.metricsRetentionDays) ||
    form.traces !== String(policy.tracesRetentionDays)
  )
}

function parseDays(raw: string, label: string, max: number): number {
  const value = Number(raw)
  if (!Number.isInteger(value) || value < 1 || value > max) {
    throw new Error(`${label} retention must be a whole number between 1 and ${max} days.`)
  }
  return value
}

/**
 * Validates the form against the same bounds the backend enforces. Returns the request body
 * or throws a user-facing error; the caller shows it in a toast.
 */
export function validatePolicyForm(
  form: PolicyFormState,
  policy: RetentionPolicy,
): UpdateRetentionPolicyInput {
  return {
    logsRetentionDays: parseDays(form.logs, 'Logs', policy.maxLogsRetentionDays),
    metricsRetentionDays: parseDays(form.metrics, 'Metrics', policy.maxMetricsRetentionDays),
    tracesRetentionDays: parseDays(form.traces, 'Traces', policy.maxTracesRetentionDays),
  }
}

/** True when the new policy keeps less history than the current one in any data type. */
export function isPolicyReduction(
  next: UpdateRetentionPolicyInput,
  current: RetentionPolicy,
): boolean {
  return (
    next.logsRetentionDays < current.logsRetentionDays ||
    next.metricsRetentionDays < current.metricsRetentionDays ||
    next.tracesRetentionDays < current.tracesRetentionDays
  )
}
