import { useEffect, useRef, useState } from 'react'
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import { useHealthChecksListQuery } from '../../api/healthchecks'
import type { StatusPage, StatusPageComponentInput } from '../../api/statusPages'
import {
  useCreateStatusPageMutation,
  useUpdateStatusPageMutation,
} from '../../api/statusPages'
import {
  STATUS_PAGE_MAX_COMPONENTS,
  statusPageFormSchema,
} from '../../features/statuspages/schemas'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import { Switch } from '../ui/switch'

interface ComponentDraft extends StatusPageComponentInput {
  key: string
}

interface StatusPageFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  statusPage: StatusPage | null
}

function draftKey(): string {
  return `component-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function StatusPageFormDialog({ open, onOpenChange, statusPage }: StatusPageFormDialogProps) {
  const isEditing = Boolean(statusPage)
  const createMutation = useCreateStatusPageMutation()
  const updateMutation = useUpdateStatusPageMutation()
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const { data: healthChecks = [], isLoading: isLoadingChecks } = useHealthChecksListQuery()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [slug, setSlug] = useState('')
  const [isActive, setIsActive] = useState(false)
  const [components, setComponents] = useState<ComponentDraft[]>([])
  const [error, setError] = useState<string | null>(null)
  const nextKey = useRef(0)

  useEffect(() => {
    if (!open) {
      return
    }
    setError(null)
    setTitle(statusPage?.title ?? '')
    setDescription(statusPage?.description ?? '')
    setSlug(statusPage?.slug ?? '')
    setIsActive(statusPage?.isActive ?? false)
    setComponents(
      (statusPage?.components ?? []).map((component) => ({
        key: `${component.id}-${component.sortOrder}`,
        name: component.name,
        linkedHealthCheckId: component.linkedHealthCheckId,
      })),
    )
  }, [open, statusPage])

  const addComponent = () => {
    if (components.length >= STATUS_PAGE_MAX_COMPONENTS) {
      return
    }
    nextKey.current += 1
    setComponents((current) => [
      ...current,
      { key: `new-${nextKey.current}-${draftKey()}`, name: '', linkedHealthCheckId: null },
    ])
  }

  const updateComponent = (key: string, patch: Partial<StatusPageComponentInput>) => {
    setComponents((current) =>
      current.map((component) => (component.key === key ? { ...component, ...patch } : component)),
    )
  }

  const moveComponent = (index: number, direction: -1 | 1) => {
    setComponents((current) => {
      const target = index + direction
      if (target < 0 || target >= current.length) {
        return current
      }
      const next = [...current]
      const [moved] = next.splice(index, 1)
      next.splice(target, 0, moved)
      return next
    })
  }

  const removeComponent = (key: string) => {
    setComponents((current) => current.filter((component) => component.key !== key))
  }

  const handleSubmit = async () => {
    const parsed = statusPageFormSchema.safeParse({
      title,
      description,
      slug,
      isActive,
      components: components.map(({ name, linkedHealthCheckId }) => ({ name, linkedHealthCheckId })),
    })

    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'Please correct the highlighted fields.')
      return
    }

    const values = parsed.data
    const payload = {
      ...values,
      description: values.description === '' ? null : values.description,
    }

    setError(null)
    try {
      if (statusPage) {
        await updateMutation.mutateAsync({ id: statusPage.id, data: payload })
        toast.success('Status page updated.')
      } else {
        await createMutation.mutateAsync(payload)
        toast.success('Status page created.')
      }
      onOpenChange(false)
    } catch (submitError) {
      setError(submitError instanceof ApiError ? submitError.message : 'Could not save the status page.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit status page' : 'Create status page'}</DialogTitle>
          <DialogDescription>
            Publish a read-only page that your customers can open without signing in.
          </DialogDescription>
        </DialogHeader>

        <form
          className="mt-6 space-y-5"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void handleSubmit()
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="status-page-title">Title</Label>
            <Input
              id="status-page-title"
              autoFocus
              value={title}
              maxLength={150}
              placeholder="Acme Platform Status"
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="status-page-description">Description</Label>
            <textarea
              id="status-page-description"
              rows={3}
              value={description}
              maxLength={500}
              placeholder="Current availability of Acme services."
              onChange={(event) => setDescription(event.target.value)}
              className="w-full rounded-md border border-white/10 bg-white/[0.03] px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60"
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
            <div className="space-y-1.5">
              <Label htmlFor="status-page-slug">Slug</Label>
              <Input
                id="status-page-slug"
                value={slug}
                maxLength={50}
                placeholder="acme-status"
                onChange={(event) => setSlug(event.target.value)}
              />
              <p className="text-xs text-slate-500">
                Public URL: {typeof window === 'undefined' ? '' : window.location.origin}/s/{slug || 'your-slug'}
              </p>
            </div>
            <div className="flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5 sm:min-w-[9rem]">
              <Label htmlFor="status-page-active" className="text-sm text-slate-200">
                Active
              </Label>
              <Switch id="status-page-active" checked={isActive} onCheckedChange={setIsActive} />
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h3 className="text-sm font-medium text-slate-200">Components</h3>
                <p className="text-xs text-slate-500">
                  Order controls how components appear on the public page.
                </p>
              </div>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={addComponent}
                disabled={components.length >= STATUS_PAGE_MAX_COMPONENTS}
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                Add component
              </Button>
            </div>

            {components.length === 0 ? (
              <div className="rounded-lg border border-dashed border-white/10 bg-white/[0.02] p-4 text-center text-sm text-slate-400">
                No components yet. Add one to show a status on the public page.
              </div>
            ) : (
              <ul className="space-y-2">
                {components.map((component, index) => (
                  <li
                    key={component.key}
                    className="grid gap-2 rounded-lg border border-white/10 bg-white/[0.02] p-3 sm:grid-cols-[1fr_1fr_auto]"
                  >
                    <Input
                      value={component.name}
                      maxLength={80}
                      placeholder="API"
                      aria-label={`Component ${index + 1} name`}
                      onChange={(event) => updateComponent(component.key, { name: event.target.value })}
                    />
                    <Select
                      value={
                        component.linkedHealthCheckId === null
                          ? 'none'
                          : String(component.linkedHealthCheckId)
                      }
                      onValueChange={(value) =>
                        updateComponent(component.key, {
                          linkedHealthCheckId: value === 'none' ? null : Number(value),
                        })
                      }
                    >
                      <SelectTrigger aria-label={`Health check for component ${index + 1}`}>
                        <SelectValue placeholder="No health check" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="none">No health check</SelectItem>
                        {healthChecks.map((check) => (
                          <SelectItem key={check.id} value={String(check.id)}>
                            {check.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={`Move component ${index + 1} up`}
                        disabled={index === 0}
                        onClick={() => moveComponent(index, -1)}
                      >
                        <ArrowUp className="h-4 w-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={`Move component ${index + 1} down`}
                        disabled={index === components.length - 1}
                        onClick={() => moveComponent(index, 1)}
                      >
                        <ArrowDown className="h-4 w-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="text-red-300 hover:text-red-200"
                        aria-label={`Remove component ${index + 1}`}
                        onClick={() => removeComponent(component.key)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            {isLoadingChecks ? <p className="text-xs text-slate-500">Loading health checks…</p> : null}
          </div>

          {error ? (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Create status page'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
