import { useEffect } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Bookmark } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { SavedLogView, SavedLogViewQuery } from '../../api/savedLogViews'
import { useCreateSavedLogViewMutation, useRenameSavedLogViewMutation } from '../../api/savedLogViews'
import { savedViewFormSchema, savedViewQueryError, type SavedViewFormValues } from '../../features/logs/savedViews'
import { FormField } from '../auth/FormField'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'

interface SavedViewFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The view being renamed, or {@code null} to create a new one. */
  view: SavedLogView | null
  /** The filters to capture when creating; required only in create mode. */
  currentQuery: SavedLogViewQuery | null
}

export function SavedViewFormDialog({ open, onOpenChange, view, currentQuery }: SavedViewFormDialogProps) {
  const isEditing = Boolean(view)
  const createMutation = useCreateSavedLogViewMutation()
  const renameMutation = useRenameSavedLogViewMutation()
  const isSubmitting = createMutation.isPending || renameMutation.isPending

  const form = useForm<SavedViewFormValues>({
    resolver: zodResolver(savedViewFormSchema),
    defaultValues: { name: '' },
  })
  const { reset, formState } = form

  useEffect(() => {
    if (open) {
      reset({ name: view?.name ?? '' })
    }
  }, [open, view, reset])

  const onSubmit = form.handleSubmit(async (values) => {
    if (!view) {
      if (!currentQuery) {
        form.setError('root', { message: 'Apply filters to the log explorer before saving a view.' })
        return
      }
      const unsafe = savedViewQueryError(currentQuery)
      if (unsafe) {
        form.setError('root', { message: unsafe })
        return
      }
    }

    try {
      if (view) {
        await renameMutation.mutateAsync({ id: view.id, name: values.name })
        toast.success('View renamed.')
      } else {
        await createMutation.mutateAsync({ name: values.name, queryParams: currentQuery as SavedLogViewQuery })
        toast.success('View saved.')
      }
      onOpenChange(false)
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Could not save the view. Please try again.'
      form.setError('root', { message })
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Rename saved view' : 'Save current view'}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? 'Change the name of this saved view.'
              : 'Save the current application, level, time range and search text for quick reuse.'}
          </DialogDescription>
        </DialogHeader>

        <form className="mt-6 space-y-4" noValidate onSubmit={onSubmit}>
          <FormField
            label="Name"
            icon={Bookmark}
            autoFocus
            autoComplete="off"
            placeholder="Production errors"
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
              {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Save view'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
