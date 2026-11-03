import { useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, FlaskConical, GripVertical, Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { LogPipelineRule } from '../../api/logPipelines'
import {
  useDeleteLogPipelineRuleMutation,
  useLogPipelineRulesQuery,
  useReorderLogPipelineRulesMutation,
} from '../../api/logPipelines'
import { useApplicationsQuery } from '../../api/useApplications'
import { LogPipelineFormDialog } from '../../components/logPipelines/LogPipelineFormDialog'
import { LogPipelineTestDialog } from '../../components/logPipelines/LogPipelineTestDialog'
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { pipelineActionLabel, summarizeAction } from '../../features/logPipelines/schemas'
import { useCanManage } from '../../features/organizations/useCanManage'

export default function LogPipelinesPage() {
  const canManage = useCanManage()
  const rulesQuery = useLogPipelineRulesQuery()
  const applicationsQuery = useApplicationsQuery()
  const reorderMutation = useReorderLogPipelineRulesMutation()
  const deleteMutation = useDeleteLogPipelineRuleMutation()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<LogPipelineRule | null>(null)
  const [testOpen, setTestOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<LogPipelineRule | null>(null)
  const [draggingId, setDraggingId] = useState<number | null>(null)

  const applications = applicationsQuery.data ?? []
  const rules = useMemo(
    () => [...(rulesQuery.data ?? [])].sort((left, right) => left.orderIndex - right.orderIndex || left.id - right.id),
    [rulesQuery.data],
  )
  const applicationNames = useMemo(
    () => new Map(applications.map((application) => [application.id, application.name])),
    [applications],
  )

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const openEdit = (rule: LogPipelineRule) => {
    setEditing(rule)
    setFormOpen(true)
  }

  const persistOrder = async (next: LogPipelineRule[]) => {
    try {
      await reorderMutation.mutateAsync(next.map((rule) => rule.id))
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not reorder pipeline rules.')
    }
  }

  const moveRule = (rule: LogPipelineRule, offset: number) => {
    const index = rules.findIndex((candidate) => candidate.id === rule.id)
    const target = index + offset
    if (index < 0 || target < 0 || target >= rules.length) {
      return
    }
    const next = [...rules]
    const [moved] = next.splice(index, 1)
    next.splice(target, 0, moved)
    void persistOrder(next)
  }

  const handleDrop = (target: LogPipelineRule) => {
    if (draggingId === null || draggingId === target.id) {
      setDraggingId(null)
      return
    }
    const sourceIndex = rules.findIndex((rule) => rule.id === draggingId)
    const targetIndex = rules.findIndex((rule) => rule.id === target.id)
    if (sourceIndex < 0 || targetIndex < 0) {
      setDraggingId(null)
      return
    }
    const next = [...rules]
    const [moved] = next.splice(sourceIndex, 1)
    next.splice(targetIndex, 0, moved)
    setDraggingId(null)
    void persistOrder(next)
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      toast.success('Pipeline rule deleted.')
      setDeleteTarget(null)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not delete the pipeline rule.')
    }
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="section-tag">Logs</p>
          <h1 className="font-display text-2xl font-semibold text-slate-100">Log pipelines</h1>
          <p className="text-sm text-slate-400">
            Parse, enrich, mask or drop log events before they are stored. Rules run top to bottom.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" onClick={() => setTestOpen(true)} disabled={applications.length === 0}>
            <FlaskConical className="h-4 w-4" aria-hidden="true" />
            Test rules
          </Button>
          {canManage ? (
            <Button type="button" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create rule
            </Button>
          ) : null}
        </div>
      </header>

      {rulesQuery.isLoading ? (
        <PipelinesSkeleton />
      ) : rulesQuery.isError ? (
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 className="font-display text-lg font-semibold text-red-200">Could not load pipeline rules</h2>
          <p className="mt-2 text-sm text-slate-400">
            {rulesQuery.error instanceof ApiError
              ? rulesQuery.error.message
              : 'Something went wrong while loading pipeline rules.'}
          </p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={() => void rulesQuery.refetch()}
            disabled={rulesQuery.isFetching}
          >
            {rulesQuery.isFetching ? 'Retrying…' : 'Retry'}
          </Button>
        </div>
      ) : rules.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
          <p className="text-sm text-slate-300">
            {canManage
              ? 'No pipeline rules yet. Create a rule to parse, enrich, mask or drop logs.'
              : 'No pipeline rules in this organization yet.'}
          </p>
          {canManage ? (
            <Button type="button" variant="secondary" className="mt-4" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create rule
            </Button>
          ) : null}
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            {canManage
              ? 'Drag rows to change the order. The same order is used by agents and backend ingestion.'
              : 'Rules are applied in this order by agents and backend ingestion.'}
          </p>
          <div className="overflow-hidden rounded-2xl border border-white/10 bg-panel/40 p-1">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10" />
                  <TableHead>Name</TableHead>
                  <TableHead>Scope</TableHead>
                  <TableHead>Filter</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Effect</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rules.map((rule, index) => (
                  <TableRow
                    key={rule.id}
                    draggable={canManage}
                    onDragStart={(event) => {
                      setDraggingId(rule.id)
                      event.dataTransfer.effectAllowed = 'move'
                      event.dataTransfer.setData('text/plain', String(rule.id))
                    }}
                    onDragOver={(event) => {
                      if (canManage) {
                        event.preventDefault()
                        event.dataTransfer.dropEffect = 'move'
                      }
                    }}
                    onDrop={(event) => {
                      event.preventDefault()
                      handleDrop(rule)
                    }}
                    onDragEnd={() => setDraggingId(null)}
                    className={draggingId === rule.id ? 'opacity-50' : undefined}
                  >
                    <TableCell>
                      {canManage ? (
                        <GripVertical className="h-4 w-4 cursor-grab text-slate-500" aria-label="Drag to reorder" />
                      ) : (
                        <span className="text-xs text-slate-500">{index + 1}</span>
                      )}
                    </TableCell>
                    <TableCell className="font-medium text-slate-100">{rule.name}</TableCell>
                    <TableCell className="text-slate-400">
                      {rule.applicationId === null
                        ? 'All applications'
                        : applicationNames.get(rule.applicationId) ?? `Application ${rule.applicationId}`}
                    </TableCell>
                    <TableCell className="max-w-xs">
                      <code className="block truncate font-mono text-xs text-slate-300" title={rule.filter}>
                        {rule.filter}
                      </code>
                    </TableCell>
                    <TableCell>
                      <Badge variant={rule.action === 'DROP' ? 'danger' : rule.action === 'MASK' ? 'warning' : 'info'}>
                        {pipelineActionLabel(rule.action)}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-xs text-xs text-slate-400">{summarizeAction(rule)}</TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-1">
                        {canManage ? (
                          <>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              aria-label={`Move ${rule.name} up`}
                              disabled={index === 0 || reorderMutation.isPending}
                              onClick={() => moveRule(rule, -1)}
                            >
                              <ArrowUp className="h-4 w-4" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              aria-label={`Move ${rule.name} down`}
                              disabled={index === rules.length - 1 || reorderMutation.isPending}
                              onClick={() => moveRule(rule, 1)}
                            >
                              <ArrowDown className="h-4 w-4" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              aria-label={`Edit ${rule.name}`}
                              onClick={() => openEdit(rule)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              className="text-red-300 hover:text-red-200"
                              aria-label={`Delete ${rule.name}`}
                              onClick={() => setDeleteTarget(rule)}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      )}

      <LogPipelineFormDialog open={formOpen} onOpenChange={setFormOpen} rule={editing} applications={applications} />
      <LogPipelineTestDialog
        open={testOpen}
        onOpenChange={setTestOpen}
        applications={applications}
        defaultApplicationId={editing?.applicationId ?? null}
      />
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
            <AlertDialogTitle>Delete pipeline rule</AlertDialogTitle>
            <AlertDialogDescription>
              This removes “{deleteTarget?.name}”. Events will no longer be processed by this rule.
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

function PipelinesSkeleton() {
  return (
    <div className="space-y-2 rounded-2xl border border-white/10 bg-panel/40 p-4" aria-busy="true">
      {[0, 1, 2, 3].map((row) => (
        <div key={row} className="flex items-center gap-4">
          <Skeleton className="h-4 w-4" />
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-6 w-24 rounded-full" />
          <Skeleton className="ml-auto h-8 w-24" />
        </div>
      ))}
    </div>
  )
}
