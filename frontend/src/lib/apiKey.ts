const VISIBLE_PREFIX_LENGTH = 4

/**
 * Masks a secret for display. The first few characters are kept as a hint so
 * users can recognise which key they are looking at; the rest is hidden.
 */
export function maskApiKey(apiKey: string): string {
  if (!apiKey) {
    return ''
  }
  if (apiKey.length <= VISIBLE_PREFIX_LENGTH) {
    return '*'.repeat(apiKey.length)
  }
  return `${apiKey.slice(0, VISIBLE_PREFIX_LENGTH)}${'*'.repeat(12)}`
}
