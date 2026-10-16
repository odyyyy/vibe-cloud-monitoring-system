import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { Dashboard } from '../../api/dashboards'
import { useDeleteDashboardMutation } from '../../api/dashboards'
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

interface DeleteDashboardDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  dashboard: Dashboard | null
  onDeleted?: () => void
}

export function DeleteDashboardDialog({ open, onOpenChange, dashboard, onDeleted }: DeleteDashboardDialogProps) {
  const deleteMutation = useDeleteDashboardMutation()

  const handleConfirm = async () => {
    if (!dashboard) {
      return
    }
    try {
      await deleteMutation.mutateAsync(dashboard.id)
      toast.success('Dashboard deleted.')
      onOpenChange(false)
      onDeleted?.()
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Could not delete the dashboard. Please try again.'
      toast.error(message)
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete dashboard</AlertDialogTitle>
          <AlertDialogDescription>
            Deleting <span className="text-slate-200">{dashboard?.name}</span> removes the dashboard and all of its
            widgets. This action cannot be undone.
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
