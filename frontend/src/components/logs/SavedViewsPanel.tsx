import { useState } from 'react'
import { Bookmark, MoreVertical, Pencil, Plus, Trash2 } from 'lucide-react'
import type { LogsQueryParams } from '../../api/logs'
import type { SavedLogView, SavedLogViewQuery } from '../../api/savedLogViews'
import { useSavedLogViewsQuery } from '../../api/savedLogViews'
import { useApplicationsQuery } from '../../api/useApplications'
import { savedViewMatchesParams, summarizeSavedViewQuery, toSavedViewQuery } from '../../features/logs/savedViews'
import { cn } from '../../lib/utils'
import { Button } from '../ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu'
import { DeleteSavedViewDialog } from './DeleteSavedViewDialog'
import { SavedViewFormDialog } from './SavedViewFormDialog'

interface SavedViewsPanelProps {
  /** The committed log query, or {@code null} when no valid query is applied yet. */
  currentParams: LogsQueryParams | null
  onApply: (view: SavedLogView) => void
}

export function SavedViewsPanel({ currentParams, onApply }: SavedViewsPanelProps) {
  const { data, isLoading, isError, refetch } = useSavedLogViewsQuery()
  const applicationsQuery = useApplicationsQuery()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<SavedLogView | null>(null)
  const [deleting, setDeleting] = useState<SavedLogView | null>(null)

  const views = data ?? []
  const applicationNames = new Map((applicationsQuery.data ?? []).map((application) => [application.id, application.name]))
  const currentQuery: SavedLogViewQuery | null = currentParams ? toSavedViewQuery(currentParams) : null

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openRename = (view: SavedLogView) => {
    setEditing(view)
    setFormOpen(true)
  }

  return (
    <aside id="saved-views" className="scroll-mt-24 rounded-2xl border border-white/10 bg-panel/60 p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-display text-sm font-semibold text-slate-200">Saved views</h2>
        <Button
          type="button"
          size="sm"
          variant="outline"
          onClick={openCreate}
          disabled={currentQuery === null}
          title={currentQuery === null ? 'Apply filters before saving a view' : undefined}
        >
          <Plus className="h-4 w-4" aria-hidden="true" />
          Save
        </Button>
      </div>

      {isLoading ? (
        <p className="mt-4 text-xs text-slate-400">Loading saved views…</p>
      ) : isError ? (
        <div className="mt-4 space-y-2">
          <p className="text-xs text-red-300">Could not load saved views.</p>
          <Button type="button" size="sm" variant="outline" onClick={() => refetch()}>
            Retry
          </Button>
        </div>
      ) : views.length === 0 ? (
        <p className="mt-4 text-xs text-slate-400">
          No saved views yet. Apply filters, then use Save to keep them here.
        </p>
      ) : (
        <ul className="mt-3 space-y-1">
          {views.map((view) => {
            const active = currentParams !== null && savedViewMatchesParams(view, currentParams)
            return (
              <li key={view.id} className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => onApply(view)}
                  aria-current={active ? 'true' : undefined}
                  className={cn(
                    'min-w-0 flex-1 rounded-lg px-2 py-1.5 text-left transition-colors hover:bg-white/5',
                    active ? 'bg-cyan-400/10' : undefined,
                  )}
                >
                  <span className="flex items-center gap-1.5 text-sm text-slate-200">
                    <Bookmark className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
                    <span className="truncate">{view.name}</span>
                  </span>
                  <span className="mt-0.5 block truncate text-xs text-slate-500">
                    {summarizeSavedViewQuery(view.queryParams, applicationNames.get(view.queryParams.applicationId))}
                  </span>
                </button>

                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      className="h-8 w-8 shrink-0"
                      aria-label={`Actions for ${view.name}`}
                    >
                      <MoreVertical className="h-4 w-4" aria-hidden="true" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onSelect={() => openRename(view)}>
                      <Pencil className="h-4 w-4" aria-hidden="true" />
                      Rename
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onSelect={() => setDeleting(view)}
                      className="text-red-300 focus:text-red-200"
                    >
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                      Delete
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </li>
            )
          })}
        </ul>
      )}

      <SavedViewFormDialog open={formOpen} onOpenChange={setFormOpen} view={editing} currentQuery={currentQuery} />
      <DeleteSavedViewDialog
        open={deleting !== null}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) {
            setDeleting(null)
          }
        }}
        view={deleting}
      />
    </aside>
  )
}
