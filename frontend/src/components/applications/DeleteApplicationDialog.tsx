import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { Application } from '../../api/applications'
import { useDeleteApplicationMutation } from '../../api/useApplications'
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

interface DeleteApplicationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  application: Application | null
}

export function DeleteApplicationDialog({ open, onOpenChange, application }: DeleteApplicationDialogProps) {
  const deleteMutation = useDeleteApplicationMutation()

  const handleConfirm = async () => {
    if (!application) {
      return
    }
    try {
      await deleteMutation.mutateAsync(application.id)
      toast.success('Application deleted.')
      onOpenChange(false)
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : 'Could not delete the application. Please try again.'
      toast.error(message)
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete application</AlertDialogTitle>
          <AlertDialogDescription>
            Deleting <span className="text-slate-200">{application?.name}</span> removes the application together
            with its associated metrics and logs. This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleteMutation.isPending}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={deleteMutation.isPending}
            onClick={(event) => {
              event.preventDefault()
              void handleConfirm()
            }}
          >
            {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
