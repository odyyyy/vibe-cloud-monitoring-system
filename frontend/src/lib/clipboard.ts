/**
 * Copies text to the clipboard. Returns `false` when the Clipboard API is
 * unavailable (e.g. insecure context or older browser) or the write fails, so
 * callers can fall back to revealing the value for manual copying instead of
 * failing silently.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (typeof navigator === 'undefined' || !navigator.clipboard) {
      return false
    }
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
