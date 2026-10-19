import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Settings2 } from 'lucide-react'
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
import { DashboardVariablesBar } from '../../components/dashboards/DashboardVariablesBar'
import { DashboardVariablesDialog } from '../../components/dashboards/DashboardVariablesDialog'
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
import { DashboardVariablesProvider } from '../../features/dashboards/DashboardVariablesContext'
import { widgetToLayout, resolveGridLayout } from '../../features/dashboards/layout'
import {
  DEFAULT_POLLING_INTERVAL_MS,
  POLLING_OPTIONS,
  sanitizePollingInterval,
} from '../../features/dashboards/polling'
import {
  DEFAULT_WIDGET_HEIGHT,
  DEFAULT_WIDGET_WIDTH,
  MAX_WIDGETS_PER_DASHBOARD,
} from '../../features/dashboards/schemas'
import { useCanManage } from '../../features/organizations/useCanManage'

export default function DashboardViewPage() {
  const canManage = useCanManage()
  const params = useParams<{ id: string }>()
  const dashboardId = params.id && /^\d+$/.test(params.id) ? Number(params.id) : null
  const queryClient = useQueryClient()

  const { data: dashboard, isLoading, isError, error, refetch, isFetching } = useDashboardQuery(dashboardId)

  const addWidget = useAddWidgetMutation(dashboardId ?? 0)
  const updateWidgetMutation = useUpdateWidgetMutation(dashboardId ?? 0)
  const deleteWidget = useDeleteWidgetMutation(dashboardId ?? 0)

  const [pollInterval, setPollInterval] = useState(DEFAULT_POLLING_INTERVAL_MS)
  const [layout, setLayout] = useState<Layout[] | null>(null)
  const [isSavingLayout, setIsSavingLayout] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [editingWidget, setEditingWidget] = useState<Widget | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Widget | null>(null)
  const [variablesOpen, setVariablesOpen] = useState(false)

  const serverLayout = useMemo<Layout[]>(() => (dashboard?.widgets ?? []).map(widgetToLayout), [dashboard])
  const serverSignature = useMemo(() => JSON.stringify(serverLayout), [serverLayout])

  useEffect(() => {
    if (!dashboard) {
      return
    }
    setLayout(serverLayout)
    // Reset the local layout only when the persisted layout actually changes
    // (initial load, add/delete, save round-trip) so an in-flight drag is never clobbered.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serverSignature])

  // Never hand a layout missing some widgets to react-grid-layout: for any child
  // without an entry it would generate a default 1x1 cell and overwrite the
  // persisted positions. Existing local (possibly just-dragged) positions win;
  // widgets the local state does not know about yet fall back to the server.
  const gridLayout = useMemo(() => resolveGridLayout(layout, serverLayout), [layout, serverLayout])

  const handleLayoutChange = useCallback((next: Layout[]) => {
    setLayout(next)
  }, [])

  const handleLayoutCommit = useCallback(
    async (next: Layout[]) => {
      if (!dashboard) {
        return
      }
      const changes = next.filter((item) => {
        const server = serverLayout.find((candidate) => candidate.i === item.i)
        return (
          server !== undefined &&
          (server.x !== item.x || server.y !== item.y || server.w !== item.w || server.h !== item.h)
        )
      })
      if (changes.length === 0) {
        return
      }
      setIsSavingLayout(true)
      try {
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
      } catch (saveError) {
        setLayout(serverLayout)
        toast.error(saveError instanceof ApiError ? saveError.message : 'Could not save the layout.')
      } finally {
        setIsSavingLayout(false)
      }
    },
    [dashboard, queryClient, serverLayout],
  )

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
    const nextY = (layout ?? serverLayout).reduce((max, item) => Math.max(max, item.y + item.h), 0)
    const created = await addWidget.mutateAsync({
      type: values.type as WidgetType,
      config: values.config,
      positionX: 0,
      positionY: nextY,
      width: DEFAULT_WIDGET_WIDTH,
      height: DEFAULT_WIDGET_HEIGHT,
    })
    setLayout((current) => [...(current ?? serverLayout), widgetToLayout(created)])
    toast.success('Widget added.')
  }

  const handleDeleteWidget = async () => {
    if (!deleteTarget) {
      return
    }
    const widgetId = deleteTarget.id
    try {
      await deleteWidget.mutateAsync(widgetId)
      setLayout((current) => (current ?? serverLayout).filter((item) => item.i !== String(widgetId)))
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
  const variables = dashboard.variables ?? []
  const items: DashboardGridItem[] = dashboard.widgets.map((widget) => ({
    id: widget.id,
    node: (
      <DashboardWidget
        widget={widget}
        showActions={canManage}
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
          {isSavingLayout ? (
            <p className="text-xs text-amber-300" role="status">
              Saving layout…
            </p>
          ) : (
            <p className="text-xs text-slate-500" role="status">
              Drag a widget header to reposition it. Changes are saved automatically.
            </p>
          )}
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

          {canManage ? (
            <>
              <Button type="button" variant="secondary" onClick={() => setVariablesOpen(true)}>
                <Settings2 className="h-4 w-4" aria-hidden="true" />
                Variables
              </Button>
              <Button type="button" variant="secondary" onClick={handleAddClick} disabled={atWidgetLimit}>
                <Plus className="h-4 w-4" aria-hidden="true" />
                Add widget
              </Button>
            </>
          ) : null}
        </div>
      </header>

      <DashboardVariablesProvider variables={variables}>
        {variables.length > 0 ? <DashboardVariablesBar variables={variables} /> : null}

        {atWidgetLimit ? (
          <p className="text-xs text-slate-500">
            This dashboard has reached the limit of {MAX_WIDGETS_PER_DASHBOARD} widgets.
          </p>
        ) : null}

        {dashboard.widgets.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-white/10 bg-panel/40 p-10 text-center">
            <p className="text-sm text-slate-300">This dashboard has no widgets yet.</p>
            {canManage ? (
              <Button type="button" variant="secondary" className="mt-4" onClick={handleAddClick}>
                <Plus className="h-4 w-4" aria-hidden="true" />
                Add your first widget
              </Button>
            ) : null}
          </div>
        ) : (
          <DashboardGrid
            items={items}
            layout={gridLayout}
            interactive={canManage}
            onLayoutChange={handleLayoutChange}
            onLayoutCommit={(next) => void handleLayoutCommit(next)}
          />
        )}
      </DashboardVariablesProvider>

      <WidgetFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        widget={editingWidget}
        onSubmit={handleSubmitWidget}
      />

      <DashboardVariablesDialog
        open={variablesOpen}
        onOpenChange={setVariablesOpen}
        dashboardId={dashboard.id}
        dashboardName={dashboard.name}
        variables={variables}
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
