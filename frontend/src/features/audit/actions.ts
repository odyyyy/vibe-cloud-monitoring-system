export interface AuditActionOption {
  value: string
  label: string
}

/**
 * Known audit actions emitted by the backend. The filter accepts any value the backend
 * supports; this list only drives the convenient dropdown, and unknown actions are still
 * displayed (and filterable when present in the URL).
 */
export const AUDIT_ACTION_OPTIONS: AuditActionOption[] = [
  { value: 'APPLICATION_DELETED', label: 'Application deleted' },
  { value: 'API_KEY_REGENERATED', label: 'API key regenerated' },
  { value: 'DASHBOARD_DELETED', label: 'Dashboard deleted' },
]

const ACTION_LABELS = new Map(AUDIT_ACTION_OPTIONS.map((option) => [option.value, option.label]))

/** Human-readable label for an action, falling back to the raw value for unknown actions. */
export function auditActionLabel(action: string): string {
  return ACTION_LABELS.get(action) ?? action
}
