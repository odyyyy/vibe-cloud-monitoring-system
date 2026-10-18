import { AUDIT_ACTION_OPTIONS } from '../../features/audit/actions'
import { instantToLocalInput, localInputToInstant } from '../../features/audit/dateRange'
import type { AuditFilters } from '../../features/audit/searchParams'
import { Button } from '../ui/button'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface AuditFilterPanelProps {
  draft: AuditFilters
  onChange: (patch: Partial<AuditFilters>) => void
  onApply: () => void
  error: string | null
  isApplying: boolean
}

export function AuditFilterPanel({ draft, onChange, onApply, error, isApplying }: AuditFilterPanelProps) {
  const options = draft.action !== '' && !AUDIT_ACTION_OPTIONS.some((option) => option.value === draft.action)
    ? [{ value: draft.action, label: draft.action }, ...AUDIT_ACTION_OPTIONS]
    : AUDIT_ACTION_OPTIONS

  return (
    <form
      className="rounded-2xl border border-white/10 bg-panel/60 p-4 sm:p-5"
      onSubmit={(event) => {
        event.preventDefault()
        onApply()
      }}
    >
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-1.5">
          <Label htmlFor="audit-action">Action</Label>
          <Select
            value={draft.action === '' ? 'ALL' : draft.action}
            onValueChange={(value) => onChange({ action: value === 'ALL' ? '' : value })}
          >
            <SelectTrigger id="audit-action">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All actions</SelectItem>
              {options.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="audit-from">From</Label>
          <Input
            id="audit-from"
            type="datetime-local"
            value={instantToLocalInput(draft.from)}
            onChange={(event) => onChange({ from: localInputToInstant(event.target.value) })}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="audit-to">To</Label>
          <Input
            id="audit-to"
            type="datetime-local"
            value={instantToLocalInput(draft.to)}
            onChange={(event) => onChange({ to: localInputToInstant(event.target.value) })}
          />
        </div>

        <div className="flex items-end">
          <Button type="submit" className="w-full" disabled={isApplying}>
            Apply
          </Button>
        </div>
      </div>

      {error ? (
        <p role="alert" className="mt-3 text-sm text-red-400">
          {error}
        </p>
      ) : null}
    </form>
  )
}
