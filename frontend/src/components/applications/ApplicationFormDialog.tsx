import { useEffect } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { Application } from '../../api/applications'
import { useCreateApplicationMutation, useUpdateApplicationMutation } from '../../api/useApplications'
import { FormField } from '../auth/FormField'
import { Button } from '../ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog'
import { Layers } from 'lucide-react'
import { applicationFormSchema, type ApplicationFormValues } from '../../features/applications/schemas'

interface ApplicationFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  application?: Application | null
  onCreated?: (application: Application) => void
}

export function ApplicationFormDialog({
  open,
  onOpenChange,
  application,
  onCreated,
}: ApplicationFormDialogProps) {
  const isEditing = Boolean(application)
  const createMutation = useCreateApplicationMutation()
  const updateMutation = useUpdateApplicationMutation()
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  const form = useForm<ApplicationFormValues>({
    resolver: zodResolver(applicationFormSchema),
    defaultValues: { name: '' },
  })

  const { reset, formState } = form

  useEffect(() => {
    if (open) {
      reset({ name: application?.name ?? '' })
    }
  }, [open, application, reset])

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      if (application) {
        await updateMutation.mutateAsync({ id: application.id, data: values })
        toast.success('Application updated.')
      } else {
        const created = await createMutation.mutateAsync(values)
        toast.success('Application created.')
        onCreated?.(created)
      }
      onOpenChange(false)
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : 'Could not save the application. Please try again.'
      form.setError('root', { message })
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit application' : 'Create application'}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? 'Update the name of this monitored application.'
              : 'Applications group the metrics and logs sent by your agents.'}
          </DialogDescription>
        </DialogHeader>

        <form className="mt-6 space-y-4" noValidate onSubmit={onSubmit}>
          <FormField
            label="Name"
            icon={Layers}
            autoFocus
            autoComplete="off"
            placeholder="My application"
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
