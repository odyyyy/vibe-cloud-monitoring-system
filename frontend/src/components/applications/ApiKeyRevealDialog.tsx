import { useCallback, useEffect, useRef, useState } from 'react'
import { Copy, KeyRound } from 'lucide-react'
import { toast } from 'sonner'
import { copyText } from '../../lib/clipboard'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'

/** How long the user must keep the Done button pressed to close the dialog. */
const HOLD_DURATION_MS = 1200

interface ApiKeyRevealDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  apiKey: string | null
  applicationName?: string
}

/**
 * Shows an API key exactly once, right after it is issued. The backend only stores a hash,
 * so this is the sole opportunity to copy the plaintext value.
 *
 * <p>To avoid closing the dialog before the key has been copied, the Done button must be
 * pressed and held: a progress fill tracks the hold and the dialog only closes once it is
 * complete. Escape, outside clicks and the corner close button are disabled for the same
 * reason, so holding Done is the only way out.
 */
export function ApiKeyRevealDialog({ open, onOpenChange, apiKey, applicationName }: ApiKeyRevealDialogProps) {
  const [progress, setProgress] = useState(0)
  const [holding, setHolding] = useState(false)
  const frameRef = useRef<number | null>(null)
  const startRef = useRef(0)
  const completedRef = useRef(false)

  const cancelFrame = useCallback(() => {
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current)
      frameRef.current = null
    }
  }, [])

  const resetHold = useCallback(() => {
    cancelFrame()
    completedRef.current = false
    setHolding(false)
    setProgress(0)
  }, [cancelFrame])

  // Reset the hold whenever the dialog is (re)opened for a (new) key.
  useEffect(() => {
    resetHold()
  }, [open, apiKey, resetHold])

  // Stop any in-flight animation frame on unmount.
  useEffect(() => cancelFrame, [cancelFrame])

  const startHold = useCallback(() => {
    if (completedRef.current || frameRef.current !== null) {
      return
    }
    setHolding(true)
    startRef.current = performance.now()

    const step = (now: number) => {
      const ratio = Math.min((now - startRef.current) / HOLD_DURATION_MS, 1)
      setProgress(ratio)
      if (ratio >= 1) {
        completedRef.current = true
        frameRef.current = null
        setHolding(false)
        onOpenChange(false)
        return
      }
      frameRef.current = requestAnimationFrame(step)
    }

    frameRef.current = requestAnimationFrame(step)
  }, [onOpenChange])

  const releaseHold = useCallback(() => {
    if (completedRef.current) {
      return
    }
    cancelFrame()
    setHolding(false)
    setProgress(0)
  }, [cancelFrame])

  const handleCopy = async () => {
    if (!apiKey) {
      return
    }
    const copied = await copyText(apiKey)
    if (copied) {
      toast.success('API key copied to clipboard.')
    } else {
      toast.info('Clipboard is unavailable. Select and copy the key manually.')
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
            API key
          </DialogTitle>
          <DialogDescription>
            Copy this key now
            {applicationName ? (
              <>
                {' '}
                for <span className="text-slate-200">{applicationName}</span>
              </>
            ) : null}
            . It will not be shown again.
          </DialogDescription>
        </DialogHeader>
        <div className="mt-6 flex items-center gap-2 rounded-lg border border-white/10 bg-white/[0.02] p-3">
          <code className="mono flex-1 break-all text-xs text-cyan-200">{apiKey}</code>
          <Button type="button" variant="secondary" size="sm" onClick={handleCopy}>
            <Copy className="h-3.5 w-3.5" aria-hidden="true" />
            Copy
          </Button>
        </div>
        <DialogFooter className="items-center sm:items-end">
          <Button
            type="button"
            aria-label="Press and hold to close"
            aria-describedby="api-key-hold-hint"
            className="relative touch-none select-none overflow-hidden"
            onPointerDown={(event) => {
              if (event.pointerType === 'mouse' && event.button !== 0) {
                return
              }
              startHold()
            }}
            onPointerUp={releaseHold}
            onPointerLeave={releaseHold}
            onPointerCancel={releaseHold}
            onKeyDown={(event) => {
              if ((event.key === ' ' || event.key === 'Enter') && !event.repeat) {
                event.preventDefault()
                startHold()
              }
            }}
            onKeyUp={(event) => {
              if (event.key === ' ' || event.key === 'Enter') {
                event.preventDefault()
                releaseHold()
              }
            }}
            onBlur={releaseHold}
          >
            <span
              aria-hidden="true"
              className="absolute inset-y-0 left-0 bg-canvas/50"
              style={{ width: `${progress * 100}%` }}
            />
            <span className="relative z-10">{holding ? 'Keep holding…' : 'Done'}</span>
          </Button>
        </DialogFooter>
        <p id="api-key-hold-hint" className="mt-3 text-center text-xs text-slate-500">
          Press and hold <span className="text-slate-400">Done</span> to close — so you do not lose the key before
          copying it.
        </p>
      </DialogContent>
    </Dialog>
  )
}
