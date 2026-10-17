import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { cn } from '../../lib/utils'
import { Button, type ButtonProps } from './button'

/** Default time the button must be held before the action fires. */
export const DEFAULT_HOLD_DURATION_MS = 1200

export interface HoldToConfirmButtonProps extends Omit<ButtonProps, 'onClick' | 'children'> {
  /** Runs once the hold completes; a plain click never triggers it. */
  onConfirm: () => void
  holdDurationMs?: number
  /** Label shown while the button is being held. */
  holdingLabel?: string
  /** Fill color of the progress overlay. */
  fillClassName?: string
  children: ReactNode
}

/**
 * Button that only fires {@link HoldToConfirmButtonProps.onConfirm} after it has been pressed
 * and held for {@link HoldToConfirmButtonProps.holdDurationMs}. A progress fill tracks the
 * hold. Releasing early (pointer up/leave/cancel, blur or key up) cancels the action without
 * any side effect.
 *
 * <p>Used for irreversible actions so that an accidental click cannot confirm them. Pointer
 * and keyboard (Space/Enter) input are both supported.
 */
export function HoldToConfirmButton({
  onConfirm,
  holdDurationMs = DEFAULT_HOLD_DURATION_MS,
  holdingLabel = 'Keep holding…',
  fillClassName = 'bg-red-500/25',
  children,
  className,
  disabled,
  ...props
}: HoldToConfirmButtonProps) {
  const [progress, setProgress] = useState(0)
  const [holding, setHolding] = useState(false)
  const frameRef = useRef<number | null>(null)
  const startRef = useRef(0)

  const cancelFrame = useCallback(() => {
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current)
      frameRef.current = null
    }
  }, [])

  // Stop any in-flight animation frame on unmount.
  useEffect(() => cancelFrame, [cancelFrame])

  const release = useCallback(() => {
    cancelFrame()
    setHolding(false)
    setProgress(0)
  }, [cancelFrame])

  const start = useCallback(() => {
    if (disabled || frameRef.current !== null) {
      return
    }
    setHolding(true)
    startRef.current = performance.now()

    const step = (now: number) => {
      const ratio = Math.min((now - startRef.current) / holdDurationMs, 1)
      if (ratio >= 1) {
        frameRef.current = null
        setHolding(false)
        setProgress(0)
        onConfirm()
        return
      }
      setProgress(ratio)
      frameRef.current = requestAnimationFrame(step)
    }

    frameRef.current = requestAnimationFrame(step)
  }, [disabled, holdDurationMs, onConfirm])

  return (
    <Button
      type="button"
      className={cn('relative touch-none select-none overflow-hidden', className)}
      disabled={disabled}
      onPointerDown={(event) => {
        if (event.pointerType === 'mouse' && event.button !== 0) {
          return
        }
        start()
      }}
      onPointerUp={release}
      onPointerLeave={release}
      onPointerCancel={release}
      onKeyDown={(event) => {
        if ((event.key === ' ' || event.key === 'Enter') && !event.repeat) {
          event.preventDefault()
          start()
        }
      }}
      onKeyUp={(event) => {
        if (event.key === ' ' || event.key === 'Enter') {
          event.preventDefault()
          release()
        }
      }}
      onBlur={release}
      {...props}
    >
      <span
        aria-hidden="true"
        className={cn('absolute inset-y-0 left-0', fillClassName)}
        style={{ width: `${progress * 100}%` }}
      />
      <span className="relative z-10">{holding ? holdingLabel : children}</span>
    </Button>
  )
}
