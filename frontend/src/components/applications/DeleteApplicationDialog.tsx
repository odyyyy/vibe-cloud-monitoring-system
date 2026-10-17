import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { Application } from '../../api/applications'
import { useDeleteApplicationMutation } from '../../api/useApplications'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../ui/alert-dialog'
import { HoldToConfirmButton } from '../ui/hold-to-confirm-button'

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
          <HoldToConfirmButton
            aria-describedby="delete-hold-hint"
            variant="outline"
            disabled={deleteMutation.isPending}
            className="border-red-500/40 bg-red-500/10 text-red-200 hover:bg-red-500/20 hover:text-red-100"
            onConfirm={() => {
              void handleConfirm()
            }}
          >
            {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
          </HoldToConfirmButton>
        </AlertDialogFooter>
        <p id="delete-hold-hint" className="mt-3 text-center text-xs text-slate-500">
          Press and hold <span className="text-slate-400">Delete</span> to confirm — this cannot be undone.
        </p>
      </AlertDialogContent>
    </AlertDialog>
  )
}
