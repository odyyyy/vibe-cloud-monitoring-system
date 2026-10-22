import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { SavedLogView } from '../../api/savedLogViews'
import { useDeleteSavedLogViewMutation } from '../../api/savedLogViews'
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

interface DeleteSavedViewDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  view: SavedLogView | null
}

export function DeleteSavedViewDialog({ open, onOpenChange, view }: DeleteSavedViewDialogProps) {
  const deleteMutation = useDeleteSavedLogViewMutation()

  const handleConfirm = async () => {
    if (!view) {
      return
    }
    try {
      await deleteMutation.mutateAsync(view.id)
      toast.success('View deleted.')
      onOpenChange(false)
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Could not delete the view. Please try again.'
      toast.error(message)
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete saved view</AlertDialogTitle>
          <AlertDialogDescription>
            Deleting <span className="text-slate-200">{view?.name}</span> removes this saved view. This action cannot be
            undone.
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
