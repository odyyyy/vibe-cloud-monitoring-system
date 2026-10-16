import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useBlocker, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Save, X } from 'lucide-react'
import { toast } from 'sonner'
import type { Layout } from 'react-grid-layout'
import { ApiError } from '../../api/client'
import type { Widget, WidgetType } from '../../api/dashboards'
import {
  dashboardQueryKey,
  updateWidget,
  useAddWidgetMutation,
  useDashboardQuery,
  useDeleteWidgetMutation,
  useUpdateWidgetMutation,
} from '../../api/dashboards'
import { DashboardGrid, type DashboardGridItem } from '../../components/dashboards/DashboardGrid'
import { DashboardWidget } from '../../components/dashboards/DashboardWidget'
import { WidgetFormDialog, type WidgetFormSubmit } from '../../components/dashboards/WidgetFormDialog'
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
import { Button } from '../../components/ui/button'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { Skeleton } from '../../components/ui/skeleton'
import {
  DEFAULT_POLLING_INTERVAL_MS,
  POLLING_OPTIONS,
  sanitizePollingInterval,
} from '../../features/dashboards/polling'
import { layoutsEqual, widgetToLayout } from '../../features/dashboards/layout'
import {
  DEFAULT_WIDGET_HEIGHT,
  DEFAULT_WIDGET_WIDTH,
  MAX_WIDGETS_PER_DASHBOARD,
} from '../../features/dashboards/schemas'

export default function DashboardViewPage() {
  const params = useParams<{ id: string }>()
  const dashboardId = params.id && /^\d+$/.test(params.id) ? Number(params.id) : null
  const queryClient = useQueryClient()

  const { data: dashboard, isLoading, isError, error, refetch, isFetching } = useDashboardQuery(dashboardId)

  const addWidget = useAddWidgetMutation(dashboardId ?? 0)
  const updateWidgetMutation = useUpdateWidgetMutation(dashboardId ?? 0)
  const deleteWidget = useDeleteWidgetMutation(dashboardId ?? 0)

  const [pollInterval, setPollInterval] = useState(DEFAULT_POLLING_INTERVAL_MS)
  const [editable, setEditable] = useState(false)
  const [draftLayout, setDraftLayout] = useState<Layout[]>([])
  const [isSaving, setIsSaving] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [editingWidget, setEditingWidget] = useState<Widget | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Widget | null>(null)

  const serverLayout = useMemo<Layout[]>(() => (dashboard?.widgets ?? []).map(widgetToLayout), [dashboard])

  useEffect(() => {
    if (!editable) {
      setDraftLayout(serverLayout)
    }
  }, [editable, serverLayout])

  const dirty = editable && !layoutsEqual(draftLayout, serverLayout)

  const blocker = useBlocker(editable && dirty)

  useEffect(() => {
    if (!dirty) {
      return
    }
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty])

  const handleLayoutChange = useCallback(
    (layout: Layout[]) => {
      if (editable) {
        setDraftLayout(layout)
      }
    },
    [editable],
  )

  const handleSave = async () => {
    if (!dashboard) {
      return
    }
    setIsSaving(true)
    try {
      const changes = draftLayout.filter((item) => {
        const server = serverLayout.find((candidate) => candidate.i === item.i)
        return (
          server !== undefined &&
          (server.x !== item.x || server.y !== item.y || server.w !== item.w || server.h !== item.h)
        )
      })
      await Promise.all(
        changes.map((item) =>
          updateWidget(dashboard.id, Number(item.i), {
            positionX: item.x,
            positionY: item.y,
            width: item.w,
            height: item.h,
          }),
        ),
      )
      await queryClient.invalidateQueries({ queryKey: dashboardQueryKey(dashboard.id) })
      toast.success('Layout saved.')
      setEditable(false)
    } catch (saveError) {
      toast.error(saveError instanceof ApiError ? saveError.message : 'Could not save the layout.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleAddClick = () => {
    setEditingWidget(null)
    setFormOpen(true)
  }

  const handleEditClick = (widget: Widget) => {
    setEditingWidget(widget)
    setFormOpen(true)
  }

  const handleSubmitWidget = async (values: WidgetFormSubmit) => {
    if (editingWidget) {
      await updateWidgetMutation.mutateAsync({ widgetId: editingWidget.id, data: values })
      toast.success('Widget updated.')
      return
    }
    const nextY = draftLayout.reduce((max, item) => Math.max(max, item.y + item.h), 0)
    const created = await addWidget.mutateAsync({
      type: values.type as WidgetType,
      config: values.config,
      positionX: 0,
      positionY: nextY,
      width: DEFAULT_WIDGET_WIDTH,
      height: DEFAULT_WIDGET_HEIGHT,
    })
    setDraftLayout((current) => [...current, widgetToLayout(created)])
    toast.success('Widget added.')
  }

  const handleDeleteWidget = async () => {
    if (!deleteTarget) {
      return
    }
    const widgetId = deleteTarget.id
    try {
      await deleteWidget.mutateAsync(widgetId)
      setDraftLayout((current) => current.filter((item) => item.i !== String(widgetId)))
      toast.success('Widget deleted.')
      setDeleteTarget(null)
    } catch (deleteError) {
      toast.error(deleteError instanceof ApiError ? deleteError.message : 'Could not delete the widget.')
    }
  }

  if (dashboardId === null) {
    return (
      <section>
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <p className="text-sm text-slate-300">Invalid dashboard id.</p>
          <Button asChild variant="outline" className="mt-4">
            <Link to="/app/dashboards">Back to dashboards</Link>
          </Button>
        </div>
      </section>
    )
  }

  if (isLoading) {
    return <DashboardViewSkeleton />
  }

  if (isError || !dashboard) {
    return (
      <section>
        <div role="alert" className="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h1 className="font-display text-lg font-semibold text-red-200">Could not load dashboard</h1>
          <p className="mt-2 text-sm text-slate-400">
            {error instanceof ApiError
              ? error.message
              : 'The dashboard could not be loaded. It may not exist or you may not have access.'}
          </p>
          <div className="mt-4 flex gap-2">
            <Button type="button" variant="outline" onClick={() => refetch()} disabled={isFetching}>
              {isFetching ? 'Retrying…' : 'Retry'}
            </Button>
            <Button asChild variant="secondary">
              <Link to="/app/dashboards">Back to dashboards</Link>
            </Button>
          </div>
        </div>
      </section>
    )
  }

  const atWidgetLimit = dashboard.widgets.length >= MAX_WIDGETS_PER_DASHBOARD
  const items: DashboardGridItem[] = dashboard.widgets.map((widget) => ({
    id: widget.id,
    node: (
      <DashboardWidget
        widget={widget}
        editable={editable}
        refetchInterval={pollInterval}
        onEdit={handleEditClick}
        onDelete={setDeleteTarget}
      />
    ),
  }))

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <Link
            to="/app/dashboards"
            className="inline-flex items-center gap-1 text-xs text-slate-400 transition-colors hover:text-slate-200"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            Dashboards
          </Link>
          <h1 className="font-display text-2xl font-semibold text-slate-100">{dashboard.name}</h1>
          {dirty ? (
            <p className="text-xs text-amber-300" role="status">
              Unsaved changes
            </p>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-2">
            <Label htmlFor="dashboard-refresh" className="text-xs text-slate-400">
              Refresh
            </Label>
            <Select
              value={String(pollInterval)}
              onValueChange={(value) => setPollInterval(sanitizePollingInterval(Number(value)))}
            >
              <SelectTrigger id="dashboard-refresh" className="h-9 w-24">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {POLLING_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={String(option.value)}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {editable ? (
            <>
              <Button type="button" variant="secondary" onClick={handleAddClick} disabled={atWidgetLimit}>
                <Plus className="h-4 w-4" aria-hidden="true" />
                Add widget
              </Button>
              <Button type="button" variant="ghost" onClick={() => setEditable(false)} disabled={isSaving}>
                <X className="h-4 w-4" aria-hidden="true" />
                Cancel
              </Button>
              <Button type="button" onClick={() => void handleSave()} disabled={isSaving || !dirty}>
                <Save className="h-4 w-4" aria-hidden="true" />
                {isSaving ? 'Saving…' : 'Save'}
              </Button>
            </>
          ) : (
            <Button type="button" variant="secondary" onClick={() => setEditable(true)}>
              Edit
            </Button>
          )}
        </div>
      </header>

      {editable && atWidgetLimit ? (
        <p className="text-xs text-slate-500">
          This dashboard has reached the limit of {MAX_WIDGETS_PER_DASHBOARD} widgets.
        </p>
      ) : null}

      {dashboard.widgets.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-white/10 bg-[#0b101b]/40 p-10 text-center">
          <p className="text-sm text-slate-300">This dashboard has no widgets yet.</p>
          <Button
            type="button"
            variant="secondary"
            className="mt-4"
            onClick={() => {
              setEditable(true)
              handleAddClick()
            }}
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
            Add your first widget
          </Button>
        </div>
      ) : (
        <DashboardGrid items={items} layout={draftLayout} editable={editable} onLayoutChange={handleLayoutChange} />
      )}

      <WidgetFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        widget={editingWidget}
        onSubmit={handleSubmitWidget}
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
            <AlertDialogTitle>Delete widget</AlertDialogTitle>
            <AlertDialogDescription>This removes the widget from the dashboard. This action cannot be undone.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteWidget.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={deleteWidget.isPending}
              onClick={(event) => {
                event.preventDefault()
                void handleDeleteWidget()
              }}
            >
              {deleteWidget.isPending ? 'Deleting…' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={blocker.state === 'blocked'}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Discard unsaved changes?</AlertDialogTitle>
            <AlertDialogDescription>
              You have unsaved layout changes. Leaving this page will discard them.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => blocker.reset?.()}>Stay</AlertDialogCancel>
            <AlertDialogAction onClick={() => blocker.proceed?.()}>Leave</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}

function DashboardViewSkeleton() {
  return (
    <section className="space-y-6" aria-busy="true">
      <Skeleton className="h-8 w-56" />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {[0, 1].map((item) => (
          <Skeleton key={item} className="h-72 w-full rounded-2xl" />
        ))}
      </div>
    </section>
  )
}
