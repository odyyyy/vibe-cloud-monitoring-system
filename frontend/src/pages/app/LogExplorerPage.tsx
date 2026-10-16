import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { LogEvent, LogsQueryParams } from '../../api/logs'
import { useLogsQuery } from '../../api/logs'
import { LogDetailModal } from '../../components/logs/LogDetailModal'
import { LogTable } from '../../components/logs/LogTable'
import { LogsFilterPanel } from '../../components/logs/LogsFilterPanel'
import { Button } from '../../components/ui/button'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { LOG_PAGE_SIZES, logsQuerySchema } from '../../features/logs/schemas'
import {
  DEFAULT_LOG_FILTERS,
  filtersFromSearchParams,
  filtersToSearchParams,
  parseLogSearchParams,
  type LogFilters,
} from '../../features/logs/searchParams'
import { TIME_RANGE_PRESETS, resolvePresetRange } from '../../features/metrics/timeRange'

export default function LogExplorerPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchKey = searchParams.toString()

  const initialFilters = useMemo<LogFilters>(
    () => ({
      ...DEFAULT_LOG_FILTERS,
      range: resolvePresetRange(TIME_RANGE_PRESETS[0]),
    }),
    [],
  )

  const [draft, setDraft] = useState<LogFilters>(() =>
    filtersFromSearchParams(searchParams, initialFilters),
  )
  const [applyError, setApplyError] = useState<string | null>(null)
  const [selectedLog, setSelectedLog] = useState<LogEvent | null>(null)

  useEffect(() => {
    setDraft(filtersFromSearchParams(new URLSearchParams(searchKey), initialFilters))
  }, [searchKey, initialFilters])

  const parsed = useMemo(() => parseLogSearchParams(new URLSearchParams(searchKey)), [searchKey])
  const params = parsed.params

  const query = useLogsQuery(params, params !== null)
  const logs = query.data?.content ?? []

  useEffect(() => {
    if (query.error instanceof ApiError && (query.error.status === 400 || query.error.status === 403)) {
      toast.error(query.error.message)
    }
  }, [query.error])

  const handleChange = (patch: Partial<LogFilters>) => {
    setDraft((current) => ({ ...current, ...patch }))
  }

  const handleApply = () => {
    const result = logsQuerySchema.safeParse({
      applicationId: draft.applicationId ?? undefined,
      level: draft.level === 'ALL' ? undefined : draft.level,
      from: draft.range?.from ?? undefined,
      to: draft.range?.to ?? undefined,
      searchText: draft.searchText.trim() === '' ? undefined : draft.searchText,
      page: 0,
      size: draft.size,
    })

    if (!result.success) {
      setApplyError(result.error.issues[0]?.message ?? 'Invalid filters')
      return
    }

    setApplyError(null)
    setSearchParams(filtersToSearchParams(result.data))
  }

  const commit = (next: LogsQueryParams) => {
    setSearchParams(filtersToSearchParams(next))
  }

  const goToPage = (page: number) => {
    if (!params) {
      return
    }
    commit({ ...params, page })
  }

  const changePageSize = (size: number) => {
    if (!params) {
      return
    }
    commit({ ...params, size, page: 0 })
  }

  const validationError = parsed.error ?? applyError
  const isRefreshing = query.isFetching && !query.isLoading

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="section-tag">Logs</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">Log explorer</h1>
        <p className="text-sm text-slate-400">
          Search and filter log events by application, level, time range and text.
        </p>
      </header>

      <LogsFilterPanel
        draft={draft}
        onChange={handleChange}
        onApply={handleApply}
        error={validationError}
        isApplying={query.isFetching}
      />

      {params ? (
        <div className="space-y-4">
          {query.isError ? (
            <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
              <h2 className="font-display text-lg font-semibold text-red-200">Could not load logs</h2>
              <p className="mt-2 text-sm text-slate-400">
                {query.error instanceof ApiError ? query.error.message : 'Something went wrong while loading logs.'}
              </p>
              <Button type="button" variant="outline" className="mt-4" onClick={() => query.refetch()} disabled={query.isFetching}>
                {query.isFetching ? 'Retrying…' : 'Retry'}
              </Button>
            </div>
          ) : (
            <div className="overflow-hidden rounded-2xl border border-white/10 bg-[#0b101b]/60">
              <div className={isRefreshing ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
                <LogTable logs={logs} isLoading={query.isLoading} onRowClick={setSelectedLog} />
              </div>
              <LogPagination
                page={query.data?.page ?? params.page}
                totalPages={query.data?.totalPages ?? 0}
                totalElements={query.data?.totalElements ?? 0}
                size={query.data?.size ?? params.size}
                disabled={query.isFetching}
                onPageChange={goToPage}
                onSizeChange={changePageSize}
              />
            </div>
          )}
        </div>
      ) : (
        <div className="flex h-72 items-center justify-center rounded-2xl border border-dashed border-white/10 bg-[#0b101b]/40">
          <p className="text-sm text-slate-400">
            {validationError ?? 'Choose an application and a time range, then search to load logs.'}
          </p>
        </div>
      )}

      <LogDetailModal
        log={selectedLog}
        open={selectedLog !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedLog(null)
          }
        }}
      />
    </section>
  )
}

interface LogPaginationProps {
  page: number
  totalPages: number
  totalElements: number
  size: number
  disabled: boolean
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

function LogPagination({
  page,
  totalPages,
  totalElements,
  size,
  disabled,
  onPageChange,
  onSizeChange,
}: LogPaginationProps) {
  const lastPage = Math.max(totalPages - 1, 0)
  const currentPage = Math.min(page, lastPage)
  const displayPage = totalPages === 0 ? 1 : currentPage + 1
  const displayTotal = Math.max(totalPages, 1)

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 border-t border-white/5 px-4 py-3">
      <p className="text-xs text-slate-400">
        {totalElements.toLocaleString()} {totalElements === 1 ? 'result' : 'results'}
      </p>

      <div className="flex items-center gap-2">
        <div className="flex items-center gap-2">
          <Label htmlFor="logs-page-size" className="text-xs text-slate-400">
            Rows
          </Label>
          <Select value={String(size)} onValueChange={(value) => onSizeChange(Number(value))} disabled={disabled}>
            <SelectTrigger id="logs-page-size" className="h-8 w-20">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {LOG_PAGE_SIZES.map((pageSize) => (
                <SelectItem key={pageSize} value={String(pageSize)}>
                  {pageSize}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onPageChange(currentPage - 1)}
            disabled={disabled || currentPage <= 0}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            Previous
          </Button>
          <span className="text-xs text-slate-400">
            Page {displayPage} of {displayTotal}
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onPageChange(currentPage + 1)}
            disabled={disabled || currentPage >= lastPage || totalPages === 0}
          >
            Next
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
    </div>
  )
}
