import { toast } from 'sonner'
import { Copy, KeyRound } from 'lucide-react'
import { copyText } from '../../lib/clipboard'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { HoldToConfirmButton } from '../ui/hold-to-confirm-button'

interface PatRevealDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  rawToken: string | null
  tokenName?: string
}

/**
 * Shows a Personal Access Token exactly once, right after it is issued.
 *
 * <p>The backend only stores a hash, so this is the only opportunity to copy the plaintext
 * value. Escape, outside clicks and the corner close button are disabled and the Done button
 * must be pressed and held, so the dialog cannot be closed before the token is copied.
 */
export function PatRevealDialog({ open, onOpenChange, rawToken, tokenName }: PatRevealDialogProps) {
  const handleCopy = async () => {
    if (!rawToken) {
      return
    }
    const copied = await copyText(rawToken)
    if (copied) {
      toast.success('Token copied to clipboard.')
    } else {
      toast.info('Clipboard is unavailable. Select and copy the token manually.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        hideCloseButton
        onInteractOutside={(event) => event.preventDefault()}
        onEscapeKeyDown={(event) => event.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <KeyRound className="h-4 w-4 text-cyan-300" aria-hidden="true" />
            Personal access token
          </DialogTitle>
          <DialogDescription>
            Copy this token now
            {tokenName ? (
              <>
                {' '}
                for <span className="text-slate-200">{tokenName}</span>
              </>
            ) : null}
            . It will not be shown again.
          </DialogDescription>
        </DialogHeader>
        <div className="mt-6 flex items-center gap-2 rounded-lg border border-white/10 bg-white/[0.02] p-3">
          <code className="mono flex-1 break-all text-xs text-cyan-200">{rawToken ?? '—'}</code>
          <Button type="button" variant="secondary" size="sm" onClick={handleCopy} disabled={!rawToken}>
            <Copy className="h-3.5 w-3.5" aria-hidden="true" />
            Copy
          </Button>
        </div>
        <DialogFooter className="items-center sm:items-end">
          <HoldToConfirmButton
            aria-label="Press and hold to close"
            aria-describedby="pat-reveal-hold-hint"
            fillClassName="bg-canvas/50"
            onConfirm={() => onOpenChange(false)}
          >
            Done
          </HoldToConfirmButton>
        </DialogFooter>
        <p id="pat-reveal-hold-hint" className="mt-3 text-center text-xs text-slate-500">
          Press and hold <span className="text-slate-400">Done</span> to close — so you do not lose the token before
          copying it.
        </p>
      </DialogContent>
    </Dialog>
  )
}
