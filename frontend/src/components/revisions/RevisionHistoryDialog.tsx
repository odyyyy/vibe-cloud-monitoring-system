import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, History, RotateCcw } from 'lucide-react'
import { toast } from 'sonner'
import type { ConfigChangeType, ConfigResourceKind, ConfigRevisionSummary } from '../../api/revisions'
import {
  useRestoreRevisionMutation,
  useRevisionQuery,
  useRevisionsQuery,
} from '../../api/revisions'
import { ApiError } from '../../api/client'
import { formatDateTime, parseUtcInstant } from '../../lib/datetime'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../ui/alert-dialog'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../ui/dialog'
import { Skeleton } from '../ui/skeleton'

interface RevisionHistoryDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  kind: ConfigResourceKind
  resourceId: number
  resourceName: string
  canRestore: boolean
  onRestored?: () => void
}

const PAGE_SIZE = 10

const CHANGE_LABELS: Record<ConfigChangeType, string> = {
  BASELINE: 'Baseline',
  CREATE: 'Created',
  UPDATE: 'Updated',
  RESTORE: 'Restored',
}

const CHANGE_VARIANTS: Record<ConfigChangeType, 'default' | 'info' | 'success' | 'warning'> = {
  BASELINE: 'warning',
  CREATE: 'success',
  UPDATE: 'info',
  RESTORE: 'default',
}

function relativeTime(value: string): string {
  const date = parseUtcInstant(value)
  if (!date) {
    return ''
  }
  const seconds = Math.round((date.getTime() - Date.now()) / 1000)
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  const absoluteSeconds = Math.abs(seconds)
  if (absoluteSeconds < 60) {
    return formatter.format(seconds, 'second')
  }
  if (absoluteSeconds < 3600) {
    return formatter.format(Math.round(seconds / 60), 'minute')
  }
  if (absoluteSeconds < 86_400) {
    return formatter.format(Math.round(seconds / 3600), 'hour')
  }
  if (absoluteSeconds < 2_592_000) {
    return formatter.format(Math.round(seconds / 86_400), 'day')
  }
  return formatter.format(Math.round(seconds / 2_592_000), 'month')
}

function formatSnapshot(snapshot: string): string {
  try {
    return JSON.stringify(JSON.parse(snapshot) as unknown, null, 2)
  } catch {
    return snapshot
  }
}

export function RevisionHistoryDialog({
  open,
  onOpenChange,
  kind,
  resourceId,
  resourceName,
  canRestore,
  onRestored,
}: RevisionHistoryDialogProps) {
  const [page, setPage] = useState(0)
  const [selectedRevision, setSelectedRevision] = useState<number | null>(null)
  const [restoreTarget, setRestoreTarget] = useState<ConfigRevisionSummary | null>(null)
  const [restoreError, setRestoreError] = useState<string | null>(null)

  const revisionsQuery = useRevisionsQuery(kind, resourceId, page, PAGE_SIZE, open)
  const revisionQuery = useRevisionQuery(kind, resourceId, selectedRevision, open)
  const restoreMutation = useRestoreRevisionMutation(kind, resourceId)

  useEffect(() => {
    if (!open) {
      setSelectedRevision(null)
      setRestoreTarget(null)
      setRestoreError(null)
      setPage(0)
    }
  }, [open])

  useEffect(() => {
    setSelectedRevision(null)
    setPage(0)
  }, [kind, resourceId])

  const revisions = revisionsQuery.data?.content ?? []
  const selected = revisionQuery.data

  const handleRestore = async () => {
    if (!restoreTarget) {
      return
    }
    setRestoreError(null)
    try {
      await restoreMutation.mutateAsync(restoreTarget.revisionNumber)
      toast.success(`Restored revision ${restoreTarget.revisionNumber}.`)
      setRestoreTarget(null)
      setSelectedRevision(null)
      onRestored?.()
    } catch (error) {
      setRestoreError(
        error instanceof ApiError ? error.message : 'Could not restore this revision.',
      )
    }
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="flex max-h-[90vh] max-w-3xl flex-col overflow-hidden">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <History className="h-5 w-5 text-cyan-300" aria-hidden="true" />
              History
            </DialogTitle>
            <DialogDescription>
              Revision history for {resourceName}. Restoring keeps the history and adds a new entry.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid min-h-0 flex-1 gap-4 overflow-hidden md:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
            <div className="min-h-0 overflow-y-auto pr-1">
              {revisionsQuery.isLoading ? (
                <div className="space-y-2" aria-busy="true">
                  {[0, 1, 2].map((item) => (
                    <Skeleton key={item} className="h-24 w-full rounded-xl" />
                  ))}
                </div>
              ) : revisionsQuery.isError ? (
                <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/5 p-4">
                  <p className="text-sm text-red-200">
                    {revisionsQuery.error instanceof ApiError
                      ? revisionsQuery.error.message
                      : 'Could not load revision history.'}
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="mt-3"
                    onClick={() => revisionsQuery.refetch()}
                    disabled={revisionsQuery.isFetching}
                  >
                    Retry
                  </Button>
                </div>
              ) : revisions.length === 0 ? (
                <div className="rounded-xl border border-dashed border-white/10 p-6 text-center">
                  <p className="text-sm text-slate-300">No history yet.</p>
                  <p className="mt-1 text-xs text-slate-500">
                    A revision appears after the next change.
                  </p>
                </div>
              ) : (
                <ol className="space-y-2">
                  {revisions.map((revision) => {
                    const isSelected = revision.revisionNumber === selectedRevision
                    return (
                      <li key={revision.revisionNumber}>
                        <div
                          className={`rounded-xl border p-3 transition-colors ${
                            isSelected
                              ? 'border-cyan-400/40 bg-cyan-400/[0.06]'
                              : 'border-white/10 bg-white/[0.02]'
                          }`}
                        >
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-mono text-sm text-slate-200">
                              #{revision.revisionNumber}
                            </span>
                            <Badge variant={CHANGE_VARIANTS[revision.changeType]}>
                              {CHANGE_LABELS[revision.changeType]}
                            </Badge>
                            <span className="ml-auto text-xs text-slate-400">
                              {revision.userDisplayName ?? 'System'}
                            </span>
                          </div>
                          <p className="mt-1 text-xs text-slate-400">
                            <time dateTime={revision.createdAt} title={revision.createdAt}>
                              {relativeTime(revision.createdAt)} · {formatDateTime(revision.createdAt)}
                            </time>
                          </p>
                          {revision.changeType === 'RESTORE' &&
                          revision.restoredFromRevisionNumber !== null ? (
                            <p className="mt-1 text-xs text-cyan-200">
                              Restored from revision {revision.restoredFromRevisionNumber}
                            </p>
                          ) : null}
                          <div className="mt-3 flex items-center gap-2">
                            <Button
                              type="button"
                              size="sm"
                              variant="secondary"
                              onClick={() => setSelectedRevision(revision.revisionNumber)}
                            >
                              View
                            </Button>
                            {canRestore ? (
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                  setRestoreError(null)
                                  setRestoreTarget(revision)
                                }}
                              >
                                <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                                Restore
                              </Button>
                            ) : null}
                          </div>
                        </div>
                      </li>
                    )
                  })}
                </ol>
              )}

              {(revisionsQuery.data?.totalPages ?? 0) > 1 ? (
                <div className="mt-3 flex items-center justify-between">
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    disabled={page === 0 || revisionsQuery.isFetching}
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                    Newer
                  </Button>
                  <span className="text-xs text-slate-500">
                    Page {page + 1} of {revisionsQuery.data?.totalPages}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={
                      revisionsQuery.isFetching ||
                      page + 1 >= (revisionsQuery.data?.totalPages ?? 1)
                    }
                  >
                    Older
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              ) : null}
            </div>

            <div className="min-h-0 overflow-y-auto rounded-xl border border-white/10 bg-slate-950/60">
              {selectedRevision === null ? (
                <div className="p-5 text-sm text-slate-400">
                  Select a revision to view its read-only snapshot.
                </div>
              ) : revisionQuery.isLoading ? (
                <div className="space-y-2 p-4" aria-busy="true">
                  <Skeleton className="h-5 w-40" />
                  <Skeleton className="h-48 w-full" />
                </div>
              ) : revisionQuery.isError ? (
                <div role="alert" className="p-5 text-sm text-red-300">
                  {revisionQuery.error instanceof ApiError
                    ? revisionQuery.error.message
                    : 'Could not load this revision.'}
                </div>
              ) : selected ? (
                <div className="p-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-slate-200">
                      Revision {selected.revisionNumber}
                    </span>
                    <Badge variant={CHANGE_VARIANTS[selected.changeType]}>
                      {CHANGE_LABELS[selected.changeType]}
                    </Badge>
                    <span className="text-xs text-slate-500">schema v{selected.schemaVersion}</span>
                  </div>
                  <p className="mt-1 text-xs text-slate-400">
                    {selected.userDisplayName ?? 'System'} · {formatDateTime(selected.createdAt)}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">{selected.createdAt}</p>
                  <pre className="mt-3 max-h-[45vh] overflow-auto whitespace-pre-wrap break-words rounded-lg bg-black/40 p-3 text-xs text-slate-300">
                    {formatSnapshot(selected.snapshot)}
                  </pre>
                </div>
              ) : null}
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={restoreTarget !== null}
        onOpenChange={(next) => {
          if (!next) {
            setRestoreTarget(null)
            setRestoreError(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Restore revision {restoreTarget?.revisionNumber}?</AlertDialogTitle>
            <AlertDialogDescription>
              The current configuration will be replaced with this snapshot. A new history entry is
              added, so you can undo the restore by restoring another revision.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {restoreError ? (
            <p role="alert" className="text-sm text-red-300">
              {restoreError}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={restoreMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={restoreMutation.isPending}
              onClick={(event) => {
                event.preventDefault()
                void handleRestore()
              }}
            >
              {restoreMutation.isPending ? 'Restoring…' : 'Restore this version'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
