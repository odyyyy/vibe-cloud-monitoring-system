import { describe, expect, it } from 'vitest'
import { mapWithConcurrency } from './concurrency'

describe('mapWithConcurrency', () => {
  it('preserves input order', async () => {
    const result = await mapWithConcurrency([3, 1, 2], 2, async (value) => value * 10)
    expect(result).toEqual([30, 10, 20])
  })

  it('returns an empty array for no items', async () => {
    expect(await mapWithConcurrency([], 4, async (value: number) => value)).toEqual([])
  })

  it('never exceeds the concurrency limit', async () => {
    let inFlight = 0
    let maxInFlight = 0
    const items = Array.from({ length: 10 }, (_, index) => index)

    await mapWithConcurrency(items, 3, async () => {
      inFlight += 1
      maxInFlight = Math.max(maxInFlight, inFlight)
      await new Promise((resolve) => setTimeout(resolve, 5))
      inFlight -= 1
      return true
    })

    expect(maxInFlight).toBeLessThanOrEqual(3)
  })

  it('clamps a limit below 1 to a single worker', async () => {
    let inFlight = 0
    let maxInFlight = 0

    await mapWithConcurrency([1, 2, 3], 0, async () => {
      inFlight += 1
      maxInFlight = Math.max(maxInFlight, inFlight)
      await new Promise((resolve) => setTimeout(resolve, 2))
      inFlight -= 1
    })

    expect(maxInFlight).toBe(1)
  })
})
