import { useEffect, useState } from 'react'

/** Tracks document visibility so polling can be paused while the tab is hidden. */
export function useDocumentVisible(): boolean {
  const [visible, setVisible] = useState(
    () => typeof document === 'undefined' || document.visibilityState !== 'hidden',
  )

  useEffect(() => {
    const handler = () => setVisible(document.visibilityState !== 'hidden')
    document.addEventListener('visibilitychange', handler)
    return () => document.removeEventListener('visibilitychange', handler)
  }, [])

  return visible
}
