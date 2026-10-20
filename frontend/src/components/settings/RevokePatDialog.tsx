import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { PatSummary } from '../../api/pats'
import { useRevokePatMutation } from '../../api/pats'
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

interface RevokePatDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  token: PatSummary | null
}

export function RevokePatDialog({ open, onOpenChange, token }: RevokePatDialogProps) {
  const revokeMutation = useRevokePatMutation()

  const handleConfirm = async () => {
    if (!token) {
      return
    }
    try {
      await revokeMutation.mutateAsync(token.id)
      toast.success('Token revoked.')
      onOpenChange(false)
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Could not revoke the token. Please try again.'
      toast.error(message)
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Revoke token</AlertDialogTitle>
          <AlertDialogDescription>
            Revoking <span className="text-slate-200">{token?.name}</span> immediately invalidates it. Any service
            using this token will lose access. This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={revokeMutation.isPending}>Cancel</AlertDialogCancel>
          <HoldToConfirmButton
            aria-describedby="revoke-pat-hold-hint"
            variant="outline"
            disabled={revokeMutation.isPending}
            className="border-red-500/40 bg-red-500/10 text-red-200 hover:bg-red-500/20 hover:text-red-100"
            onConfirm={() => {
              void handleConfirm()
            }}
          >
            {revokeMutation.isPending ? 'Revoking…' : 'Revoke'}
          </HoldToConfirmButton>
        </AlertDialogFooter>
        <p id="revoke-pat-hold-hint" className="mt-3 text-center text-xs text-slate-500">
          Press and hold <span className="text-slate-400">Revoke</span> to confirm — this cannot be undone.
        </p>
      </AlertDialogContent>
    </AlertDialog>
  )
}
