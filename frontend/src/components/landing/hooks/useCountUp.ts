import { useEffect, useRef, useState } from 'react'

export const useCountUp = (target: number, duration = 1600, decimals = 0): number => {
  const [value, setValue] = useState(0)
  const startRef = useRef<number | null>(null)

  useEffect(() => {
    let raf = 0
    const step = (now: number) => {
      if (startRef.current === null) startRef.current = now
      const progress = Math.min((now - startRef.current) / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      setValue(parseFloat((target * eased).toFixed(decimals)))
      if (progress < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [target, duration, decimals])

  return value
}