import { useState } from 'react'
import { Copy, ExternalLink, Globe, Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { StatusPage } from '../../api/statusPages'
import {
  useDeleteStatusPageMutation,
  useStatusPagesQuery,
} from '../../api/statusPages'
import { StatusPageFormDialog } from '../../components/statuspages/StatusPageFormDialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../../components/ui/alert-dialog'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import { useCanManage } from '../../features/organizations/useCanManage'
import { copyText } from '../../lib/clipboard'

function publicUrl(slug: string): string {
  return `${window.location.origin}/s/${slug}`
}

export default function StatusPagesPage() {
  const canManage = useCanManage()
  const { data, isLoading, isError, error, isFetching, refetch } = useStatusPagesQuery()
  const deleteMutation = useDeleteStatusPageMutation()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<StatusPage | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<StatusPage | null>(null)

  const pages = data ?? []

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (page: StatusPage) => {
    setEditing(page)
    setFormOpen(true)
  }

  const handleCopy = async (page: StatusPage) => {
    const copied = await copyText(publicUrl(page.slug))
    if (copied) {
      toast.success('Public link copied.')
    } else {
      toast.error('Could not copy the link. Copy it from the address bar instead.')
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success('Status page deleted.')
      setDeleteTarget(null)
    } catch (deleteError) {
      toast.error(deleteError instanceof ApiError ? deleteError.message : 'Could not delete the status page.')
    }
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Communications</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Status Pages</h1>
          <p className="text-sm text-slate-400">
            Share a public, read-only view of your health checks with your customers.
          </p>
        </div>
        {canManage ? (
          <Button type="button" onClick={openCreate}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create status page
          </Button>
        ) : null}
      </header>

      {isLoading ? (
        <StatusPagesSkeleton />
      ) : isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load status pages</h2>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError ? error.message : 'Something went wrong while loading status pages.'}
          </p>
          <Button type="button" variant="outline" className="mt-4" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : pages.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
          <p className="text-sm text-slate-300">
            {canManage
              ? 'No status pages yet. Create one to share your service health.'
              : 'No status pages in this organization yet.'}
          </p>
          {canManage ? (
            <Button type="button" variant="secondary" className="mt-4" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create status page
            </Button>
          ) : null}
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {pages.map((page) => (
            <article key={page.id} className="rounded-2xl border border-white/10 bg-panel/60 p-5">
              <div className="flex items-start gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-white/10 bg-white/[0.03]">
                  <Globe className="h-5 w-5 text-cyan-300" aria-hidden="true" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="truncate font-display text-base font-semibold text-slate-100">{page.title}</h2>
                    <Badge variant={page.isActive ? 'success' : 'muted'}>
                      {page.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                  </div>
                  <p className="mt-0.5 text-xs text-slate-500">/s/{page.slug}</p>
                  {page.description ? (
                    <p className="mt-2 line-clamp-2 text-sm text-slate-400">{page.description}</p>
                  ) : null}
                </div>
              </div>

              <p className="mt-4 text-sm text-slate-400">
                {page.components.length} {page.components.length === 1 ? 'component' : 'components'}
              </p>

              <div className="mt-3 flex items-center gap-2 rounded-lg border border-white/5 bg-white/[0.02] px-3 py-2">
                <code className="min-w-0 flex-1 truncate text-xs text-slate-400">{publicUrl(page.slug)}</code>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label={`Copy public link for ${page.title}`}
                  onClick={() => void handleCopy(page)}
                >
                  <Copy className="h-4 w-4" />
                </Button>
                <Button asChild variant="ghost" size="icon" aria-label={`Open public page for ${page.title}`}>
                  <a href={publicUrl(page.slug)} target="_blank" rel="noreferrer">
                    <ExternalLink className="h-4 w-4" />
                  </a>
                </Button>
              </div>

              {canManage ? (
                <div className="mt-4 flex items-center justify-end gap-2">
                  <Button type="button" variant="secondary" size="sm" onClick={() => openEdit(page)}>
                    <Pencil className="h-4 w-4" aria-hidden="true" />
                    Edit
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="text-red-300 hover:text-red-200"
                    aria-label={`Delete ${page.title}`}
                    onClick={() => setDeleteTarget(page)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}

      <StatusPageFormDialog open={formOpen} onOpenChange={setFormOpen} statusPage={editing} />

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete status page</AlertDialogTitle>
            <AlertDialogDescription>
              This removes “{deleteTarget?.title}” and its public link. The linked health checks are not deleted.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={(event) => {
                event.preventDefault()
                void handleDelete()
              }}
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}

function StatusPagesSkeleton() {
  return (
    <div aria-busy="true" className="grid gap-4 lg:grid-cols-2">
      {[0, 1].map((card) => (
        <div key={card} className="rounded-2xl border border-white/10 bg-panel/60 p-5">
          <div className="flex items-center gap-3">
            <Skeleton className="h-10 w-10 rounded-xl" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3 w-24" />
            </div>
          </div>
          <Skeleton className="mt-4 h-4 w-24" />
          <Skeleton className="mt-3 h-10 w-full" />
        </div>
      ))}
    </div>
  )
}
