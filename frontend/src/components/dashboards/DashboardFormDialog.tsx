import { useEffect } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { LayoutGrid } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { Dashboard } from '../../api/dashboards'
import { useCreateDashboardMutation, useRenameDashboardMutation } from '../../api/dashboards'
import { dashboardFormSchema, type DashboardFormValues } from '../../features/dashboards/schemas'
import { FormField } from '../auth/FormField'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'

interface DashboardFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  dashboard: Dashboard | null
}

export function DashboardFormDialog({ open, onOpenChange, dashboard }: DashboardFormDialogProps) {
  const isEditing = Boolean(dashboard)
  const createMutation = useCreateDashboardMutation()
  const renameMutation = useRenameDashboardMutation(dashboard?.id ?? null)
  const isSubmitting = createMutation.isPending || renameMutation.isPending

  const form = useForm<DashboardFormValues>({
    resolver: zodResolver(dashboardFormSchema),
    defaultValues: { name: '' },
  })

  const { reset, formState } = form

  useEffect(() => {
    if (open) {
      reset({ name: dashboard?.name ?? '' })
    }
  }, [open, dashboard, reset])

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      if (dashboard) {
        await renameMutation.mutateAsync({ id: dashboard.id, name: values.name })
        toast.success('Dashboard renamed.')
      } else {
        await createMutation.mutateAsync(values.name)
        toast.success('Dashboard created.')
      }
      onOpenChange(false)
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Could not save the dashboard. Please try again.'
      form.setError('root', { message })
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Rename dashboard' : 'Create dashboard'}</DialogTitle>
          <DialogDescription>
            {isEditing ? 'Change the name of this dashboard.' : 'Dashboards group widgets that visualize your telemetry.'}
          </DialogDescription>
        </DialogHeader>

        <form className="mt-6 space-y-4" noValidate onSubmit={onSubmit}>
          <FormField
            label="Name"
            icon={LayoutGrid}
            autoFocus
            autoComplete="off"
            placeholder="Production overview"
            error={formState.errors.name?.message}
            {...form.register('name')}
          />

          {formState.errors.root?.message ? (
            <p role="alert" className="text-sm text-red-400">
              {formState.errors.root.message}
            </p>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
