/**
 * Maps items with a bounded number of concurrent async operations, preserving order.
 *
 * Useful for fan-out requests that must not flood the backend. The limit is clamped to at
 * least 1; a limit larger than the item count simply starts one worker per item.
 */
export async function mapWithConcurrency<T, R>(
  items: readonly T[],
  limit: number,
  mapper: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  if (items.length === 0) {
    return []
  }
  const effectiveLimit = Math.min(Math.max(1, Math.floor(limit)), items.length)
  const results = new Array<R>(items.length)
  let nextIndex = 0

  async function worker(): Promise<void> {
    for (;;) {
      const current = nextIndex
      nextIndex += 1
      if (current >= items.length) {
        return
      }
      results[current] = await mapper(items[current], current)
    }
  }

  await Promise.all(Array.from({ length: effectiveLimit }, () => worker()))
  return results
}
