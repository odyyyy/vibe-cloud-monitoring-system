import type { LogLevelFilter, LogFilters } from '../../features/logs/searchParams'
import { LOG_LEVELS, MAX_SEARCH_TEXT_LENGTH } from '../../features/logs/schemas'
import { ApplicationSelect } from '../filters/ApplicationSelect'
import { TimeRangePicker } from '../filters/TimeRangePicker'
import { ExportMenu } from '../ExportMenu'
import type { ExportFormat } from '../../api/client'
import { Button } from '../ui/button'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

const LEVEL_OPTIONS: LogLevelFilter[] = ['ALL', ...LOG_LEVELS]

interface LogsFilterPanelProps {
  draft: LogFilters
  onChange: (patch: Partial<LogFilters>) => void
  onApply: () => void
  onExport: (format: ExportFormat) => void
  error: string | null
  isApplying: boolean
  isExporting: boolean
  canExport: boolean
}

export function LogsFilterPanel({
  draft,
  onChange,
  onApply,
  onExport,
  error,
  isApplying,
  isExporting,
  canExport,
}: LogsFilterPanelProps) {
  return (
    <form
      className="rounded-2xl border border-white/10 bg-panel/60 p-4 sm:p-5"
      onSubmit={(event) => {
        event.preventDefault()
        onApply()
      }}
    >
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="logs-application">Application</Label>
          <ApplicationSelect
            id="logs-application"
            value={draft.applicationId}
            onChange={(value) => onChange({ applicationId: value })}
          />
        </div>

        <div className="space-y-1.5">
          <Label>Time range</Label>
          <TimeRangePicker value={draft.range} onChange={(range) => onChange({ range })} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="logs-level">Level</Label>
          <Select
            value={draft.level}
            onValueChange={(value) => onChange({ level: value as LogLevelFilter })}
          >
            <SelectTrigger id="logs-level">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {LEVEL_OPTIONS.map((level) => (
                <SelectItem key={level} value={level}>
                  {level === 'ALL' ? 'All levels' : level}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5 md:col-span-2">
          <Label htmlFor="logs-search">Search text</Label>
          <Input
            id="logs-search"
            placeholder="Search messages…"
            autoComplete="off"
            maxLength={MAX_SEARCH_TEXT_LENGTH}
            value={draft.searchText}
            onChange={(event) => onChange({ searchText: event.target.value })}
          />
        </div>

        <div className="flex items-end gap-2">
          <Button type="submit" className="flex-1" disabled={isApplying}>
            Search
          </Button>
          <ExportMenu onExport={onExport} disabled={!canExport} isExporting={isExporting} />
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
