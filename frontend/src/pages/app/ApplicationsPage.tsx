import { useState } from 'react'
import { Plus } from 'lucide-react'
import { ApiError } from '../../api/client'
import type { Application, CreatedApplication } from '../../api/applications'
import { useApplicationsQuery } from '../../api/useApplications'
import { ApiKeyRevealDialog } from '../../components/applications/ApiKeyRevealDialog'
import { ApplicationFormDialog } from '../../components/applications/ApplicationFormDialog'
import { ApplicationsTable } from '../../components/applications/ApplicationsTable'
import { DeleteApplicationDialog } from '../../components/applications/DeleteApplicationDialog'
import { RegenerateKeyDialog } from '../../components/applications/RegenerateKeyDialog'
import { Button } from '../../components/ui/button'
import { Skeleton } from '../../components/ui/skeleton'
import { useCanManage } from '../../features/organizations/useCanManage'

export default function ApplicationsPage() {
  const { data, isLoading, isError, error, isFetching, refetch } = useApplicationsQuery()
  const canManage = useCanManage()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Application | null>(null)
  const [regenerateTarget, setRegenerateTarget] = useState<Application | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Application | null>(null)
  const [createdKey, setCreatedKey] = useState<{ apiKey: string; name: string } | null>(null)

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (application: Application) => {
    setEditing(application)
    setFormOpen(true)
  }

  const handleCreated = (application: CreatedApplication) => {
    setCreatedKey({ apiKey: application.apiKey, name: application.name })
  }

  const applications = data ?? []

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Applications</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Applications</h1>
          <p className="text-sm text-slate-400">
            Create and manage the applications your agents send telemetry for.
          </p>
        </div>
        {canManage ? (
          <Button type="button" onClick={openCreate}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Create Application
          </Button>
        ) : null}
      </header>

      {isLoading ? (
        <ApplicationsTableSkeleton />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => refetch()} isRetrying={isFetching} />
      ) : applications.length === 0 ? (
        <EmptyState onCreate={openCreate} canManage={canManage} />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-white/10 bg-panel/60">
          <ApplicationsTable
            applications={applications}
            canManage={canManage}
            onEdit={openEdit}
            onRegenerate={setRegenerateTarget}
            onDelete={setDeleteTarget}
          />
        </div>
      )}

      <ApplicationFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        application={editing}
        onCreated={handleCreated}
      />
      <ApiKeyRevealDialog
        open={createdKey !== null}
        onOpenChange={(open) => {
          if (!open) {
            setCreatedKey(null)
          }
        }}
        apiKey={createdKey?.apiKey ?? null}
        applicationName={createdKey?.name}
      />
      <RegenerateKeyDialog
        open={regenerateTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRegenerateTarget(null)
          }
        }}
        application={regenerateTarget}
      />
      <DeleteApplicationDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null)
          }
        }}
        application={deleteTarget}
      />
    </section>
  )
}

function ErrorState({
  error,
  onRetry,
  isRetrying,
}: {
  error: unknown
  onRetry: () => void
  isRetrying: boolean
}) {
  const message =
    error instanceof ApiError && error.status === 403
      ? 'You do not have permission to view these applications.'
      : error instanceof ApiError
        ? error.message
        : 'Something went wrong while loading applications.'

  return (
    <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
      <h2 className="font-display text-lg font-semibold text-red-200">Could not load applications</h2>
      <p className="mt-2 text-sm text-slate-400">{message}</p>
      <Button type="button" variant="outline" className="mt-4" onClick={onRetry} disabled={isRetrying}>
        {isRetrying ? 'Retrying…' : 'Retry'}
      </Button>
    </div>
  )
}

function EmptyState({ onCreate, canManage }: { onCreate: () => void; canManage: boolean }) {
  return (
    <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
      <p className="text-sm text-slate-300">
        {canManage ? 'No applications yet. Create your first application.' : 'No applications in this organization yet.'}
      </p>
      {canManage ? (
        <Button type="button" variant="secondary" className="mt-4" onClick={onCreate}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          Create Application
        </Button>
      ) : null}
    </div>
  )
}

function ApplicationsTableSkeleton() {
  return (
    <div aria-busy="true" className="overflow-hidden rounded-2xl border border-white/10 bg-panel/60 p-4">
      <div className="space-y-3">
        {[0, 1, 2, 3, 4].map((row) => (
          <div key={row} className="flex items-center gap-4">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-4 w-36" />
            <Skeleton className="ml-auto h-8 w-8 rounded-md" />
          </div>
        ))}
      </div>
    </div>
  )
}
