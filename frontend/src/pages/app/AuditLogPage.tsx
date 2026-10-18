import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import type { AuditEventsQueryParams } from '../../api/audit'
import { useAuditEventsQuery } from '../../api/audit'
import { ApiError } from '../../api/client'
import { AuditEventsTable } from '../../components/audit/AuditEventsTable'
import { AuditFilterPanel } from '../../components/audit/AuditFilterPanel'
import { Button } from '../../components/ui/button'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { AUDIT_PAGE_SIZES, auditQuerySchema } from '../../features/audit/schemas'
import {
  DEFAULT_AUDIT_FILTERS,
  filtersFromSearchParams,
  filtersToSearchParams,
  parseAuditSearchParams,
  type AuditFilters,
} from '../../features/audit/searchParams'

export default function AuditLogPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchKey = searchParams.toString()

  const [draft, setDraft] = useState<AuditFilters>(() =>
    filtersFromSearchParams(searchParams, DEFAULT_AUDIT_FILTERS),
  )
  const [applyError, setApplyError] = useState<string | null>(null)

  useEffect(() => {
    setDraft(filtersFromSearchParams(new URLSearchParams(searchKey), DEFAULT_AUDIT_FILTERS))
  }, [searchKey])

  const parsed = useMemo(() => parseAuditSearchParams(new URLSearchParams(searchKey)), [searchKey])
  const params = parsed.params

  const query = useAuditEventsQuery(params)
  const events = query.data?.content ?? []

  useEffect(() => {
    if (query.error instanceof ApiError && (query.error.status === 400 || query.error.status === 403)) {
      toast.error(query.error.message)
    }
  }, [query.error])

  const handleChange = (patch: Partial<AuditFilters>) => {
    setDraft((current) => ({ ...current, ...patch }))
  }

  const handleApply = () => {
    const result = auditQuerySchema.safeParse({
      action: draft.action.trim() === '' ? undefined : draft.action.trim(),
      from: draft.from || undefined,
      to: draft.to || undefined,
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

  const commit = (next: AuditEventsQueryParams) => {
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
        <p className="section-tag">Security</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">Audit log</h1>
        <p className="text-sm text-slate-400">
          A read-only, immutable record of security-relevant actions performed in your workspace, newest first.
        </p>
      </header>

      <AuditFilterPanel
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
              <h2 className="font-display text-lg font-semibold text-red-200">Could not load audit events</h2>
              <p className="mt-2 text-sm text-slate-400">
                {query.error instanceof ApiError
                  ? query.error.message
                  : 'Something went wrong while loading audit events.'}
              </p>
              <Button
                type="button"
                variant="outline"
                className="mt-4"
                onClick={() => query.refetch()}
                disabled={query.isFetching}
              >
                {query.isFetching ? 'Retrying…' : 'Retry'}
              </Button>
            </div>
          ) : (
            <div className="overflow-hidden rounded-2xl border border-white/10 bg-panel/60">
              <div className={isRefreshing ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
                <AuditEventsTable events={events} isLoading={query.isLoading} />
              </div>
              <AuditPagination
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
        <div className="flex h-72 items-center justify-center rounded-2xl border border-dashed border-white/10 bg-panel/40">
          <p className="text-sm text-slate-400">
            {validationError ?? 'Adjust the filters and apply them to load audit events.'}
          </p>
        </div>
      )}
    </section>
  )
}

interface AuditPaginationProps {
  page: number
  totalPages: number
  totalElements: number
  size: number
  disabled: boolean
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

function AuditPagination({
  page,
  totalPages,
  totalElements,
  size,
  disabled,
  onPageChange,
  onSizeChange,
}: AuditPaginationProps) {
  const lastPage = Math.max(totalPages - 1, 0)
  const currentPage = Math.min(page, lastPage)
  const displayPage = totalPages === 0 ? 1 : currentPage + 1
  const displayTotal = Math.max(totalPages, 1)

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 border-t border-white/5 px-4 py-3">
      <p className="text-xs text-slate-400">
        {totalElements.toLocaleString()} {totalElements === 1 ? 'event' : 'events'}
      </p>

      <div className="flex items-center gap-2">
        <div className="flex items-center gap-2">
          <Label htmlFor="audit-page-size" className="text-xs text-slate-400">
            Rows
          </Label>
          <Select value={String(size)} onValueChange={(value) => onSizeChange(Number(value))} disabled={disabled}>
            <SelectTrigger id="audit-page-size" className="h-8 w-20">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {AUDIT_PAGE_SIZES.map((pageSize) => (
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
