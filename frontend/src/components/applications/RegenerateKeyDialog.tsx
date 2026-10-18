import { useEffect, useState } from 'react'
import { Copy, KeyRound, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { Application } from '../../api/applications'
import { useRegenerateApiKeyMutation } from '../../api/useApplications'
import { copyText } from '../../lib/clipboard'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { HoldToConfirmButton } from '../ui/hold-to-confirm-button'

interface RegenerateKeyDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  application: Application | null
  /** Notified with the new plaintext key so callers (e.g. the onboarding wizard) can use it. */
  onRegenerated?: (apiKey: string) => void
}

export function RegenerateKeyDialog({
  open,
  onOpenChange,
  application,
  onRegenerated,
}: RegenerateKeyDialogProps) {
  const regenerateMutation = useRegenerateApiKeyMutation()
  const [newKey, setNewKey] = useState<string | null>(null)

  useEffect(() => {
    if (!open) {
      setNewKey(null)
    }
  }, [open])

  const handleConfirm = async () => {
    if (!application) {
      return
    }
    try {
      const result = await regenerateMutation.mutateAsync(application.id)
      setNewKey(result.apiKey)
      onRegenerated?.(result.apiKey)
      toast.success('A new API key has been generated.')
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : 'Could not regenerate the API key. Please try again.'
      toast.error(message)
    }
  }

  const handleCopy = async () => {
    if (!newKey) {
      return
    }
    const copied = await copyText(newKey)
    if (copied) {
      toast.success('New API key copied to clipboard.')
    } else {
      toast.info('Clipboard is unavailable. Select and copy the key manually.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        {newKey ? (
          <>
            <DialogHeader>
              <DialogTitle>New API key</DialogTitle>
              <DialogDescription>
                Copy this key now. It will not be shown again in full after you close this dialog.
              </DialogDescription>
            </DialogHeader>
            <div className="mt-6 flex items-center gap-2 rounded-lg border border-white/10 bg-white/[0.02] p-3">
              <code className="mono flex-1 break-all text-xs text-cyan-200">{newKey}</code>
              <Button type="button" variant="secondary" size="sm" onClick={handleCopy}>
                <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                Copy
              </Button>
            </div>
            <DialogFooter>
              <Button type="button" onClick={() => onOpenChange(false)}>
                Done
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <TriangleAlert className="h-4 w-4 text-amber-300" aria-hidden="true" />
                Regenerate API key
              </DialogTitle>
              <DialogDescription>
                The current key for <span className="text-slate-200">{application?.name}</span> will stop working
                immediately. All agents using it will disconnect and stop sending telemetry until they are
                reconfigured with the new key.
              </DialogDescription>
            </DialogHeader>

            <div className="mt-6 flex items-start gap-3 rounded-lg border border-amber-400/20 bg-amber-400/5 p-3 text-sm text-amber-200">
              <KeyRound className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <p>This action cannot be undone. The previous key is invalidated permanently.</p>
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="secondary"
                onClick={() => onOpenChange(false)}
                disabled={regenerateMutation.isPending}
              >
                Cancel
              </Button>
              <HoldToConfirmButton
                aria-describedby="regenerate-hold-hint"
                disabled={regenerateMutation.isPending}
                fillClassName="bg-amber-400/25"
                onConfirm={() => {
                  void handleConfirm()
                }}
              >
                {regenerateMutation.isPending ? 'Regenerating…' : 'Regenerate key'}
              </HoldToConfirmButton>
            </DialogFooter>
            <p id="regenerate-hold-hint" className="mt-3 text-center text-xs text-slate-500">
              Press and hold <span className="text-slate-400">Regenerate key</span> to confirm — the new key is shown
              only once, so you must copy it immediately.
            </p>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
