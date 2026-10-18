import { useMemo, type CSSProperties } from 'react'

interface ConfettiPiece {
  left: number
  delay: number
  duration: number
  drift: number
  rotation: number
  size: number
  color: string
}

const COLORS = ['#22d3ee', '#a78bfa', '#a3e635', '#f59e0b', '#fb7185']

/** Lightweight, dependency-free confetti burst used on the wizard's success step. */
export function Confetti({ pieces = 26 }: { pieces?: number }) {
  const confetti = useMemo<ConfettiPiece[]>(
    () =>
      Array.from({ length: pieces }, (_, index) => ({
        left: Math.random() * 100,
        delay: Math.random() * 0.6,
        duration: 1.6 + Math.random() * 1.2,
        drift: -40 + Math.random() * 80,
        rotation: 360 + Math.random() * 540,
        size: 5 + Math.random() * 5,
        color: COLORS[index % COLORS.length],
      })),
    [pieces],
  )

  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      {confetti.map((piece, index) => (
        <span
          key={index}
          className="confetti-piece"
          style={{
            left: `${piece.left}%`,
            width: `${piece.size}px`,
            height: `${piece.size * 0.6}px`,
            backgroundColor: piece.color,
            animationDelay: `${piece.delay}s`,
            animationDuration: `${piece.duration}s`,
            '--confetti-drift': `${piece.drift}px`,
            '--confetti-rotation': `${piece.rotation}deg`,
          } as CSSProperties}
        />
      ))}
    </div>
  )
}
