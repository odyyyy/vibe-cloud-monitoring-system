import { z } from 'zod'
import type { LogsQueryParams } from '../../api/logs'
import type { SavedLogView, SavedLogViewQuery } from '../../api/savedLogViews'
import { DEFAULT_LOG_PAGE_SIZE } from './schemas'
import { filtersToSearchParams } from './searchParams'

export const SAVED_VIEW_NAME_MAX_LENGTH = 100

export const savedViewNameSchema = z
  .string()
  .trim()
  .min(1, 'Name is required')
  .max(SAVED_VIEW_NAME_MAX_LENGTH, `Name must be at most ${SAVED_VIEW_NAME_MAX_LENGTH} characters`)

export const savedViewFormSchema = z.object({ name: savedViewNameSchema })
export type SavedViewFormValues = z.infer<typeof savedViewFormSchema>

const UNSAFE_CHARACTERS = /[<>]/

/**
 * Maps committed log query parameters to the saved-view payload. Pagination is intentionally
 * dropped: a saved view captures the filters, not the page the user happened to be on.
 */
export function toSavedViewQuery(params: LogsQueryParams): SavedLogViewQuery {
  return {
    applicationId: params.applicationId,
    level: params.level,
    searchText: params.searchText,
    timeRange: { from: params.from, to: params.to },
  }
}

/** Converts a saved view back into the URL search params understood by the log explorer. */
export function savedViewToSearchParams(view: SavedLogView): Record<string, string> {
  return filtersToSearchParams({
    applicationId: view.queryParams.applicationId,
    level: view.queryParams.level,
    from: view.queryParams.timeRange.from,
    to: view.queryParams.timeRange.to,
    searchText: view.queryParams.searchText,
    page: 0,
    size: DEFAULT_LOG_PAGE_SIZE,
  })
}

/**
 * Client-side mirror of the backend rule that rejects angle brackets in the free-text filter.
 * Catching it here keeps the "Save current view" dialog from round-tripping an avoidable 400.
 */
export function savedViewQueryError(query: SavedLogViewQuery): string | null {
  if (query.searchText && UNSAFE_CHARACTERS.test(query.searchText)) {
    return "Search text cannot contain '<' or '>' characters."
  }
  return null
}

/** True when a saved view reproduces the given committed log query parameters. */
export function savedViewMatchesParams(view: SavedLogView, params: LogsQueryParams): boolean {
  const fromView = savedViewToSearchParams(view)
  const fromParams = filtersToSearchParams(params)
  const keys = new Set([...Object.keys(fromView), ...Object.keys(fromParams)])
  for (const key of keys) {
    if (fromView[key] !== fromParams[key]) {
      return false
    }
  }
  return true
}

/** Short human-readable summary shown in the saved-views list. */
export function summarizeSavedViewQuery(query: SavedLogViewQuery, applicationName?: string): string {
  const application = applicationName ?? `Application ${query.applicationId}`
  const level = query.level ?? 'All levels'
  const parts = [application, level]
  if (query.searchText) {
    parts.push(`"${query.searchText}"`)
  }
  return parts.join(' · ')
}
