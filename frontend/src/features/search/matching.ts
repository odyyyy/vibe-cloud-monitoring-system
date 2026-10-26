import type { PaletteEntry } from './registry'

export interface RankedEntry<T extends PaletteEntry = PaletteEntry> {
  entry: T
  score: number
}

function normalize(value: string): string {
  return value.trim().toLowerCase()
}

function isSubsequence(needle: string, haystack: string): boolean {
  if (needle.length === 0) {
    return true
  }
  let index = 0
  for (const character of haystack) {
    if (character === needle[index]) {
      index++
      if (index === needle.length) {
        return true
      }
    }
  }
  return false
}

/**
 * Rank a single entry against a query. Lower scores rank first; `null` means no match.
 *
 * <p>The tiers deliberately favour what the user can see in the list: exact and prefix title
 * matches, then keywords, then descriptions and finally fuzzy subsequences.
 */
export function scoreEntry(entry: PaletteEntry, rawQuery: string): number | null {
  const query = normalize(rawQuery)
  if (query === '') {
    return 0
  }

  const title = normalize(entry.title)
  const description = normalize(entry.description)
  const keywords = entry.keywords.map(normalize)

  if (title === query) {
    return 0
  }
  if (title.startsWith(query)) {
    return 1
  }
  if (title.split(/\s+/).some((word) => word.startsWith(query))) {
    return 2
  }
  if (keywords.some((keyword) => keyword === query)) {
    return 3
  }
  if (keywords.some((keyword) => keyword.startsWith(query))) {
    return 4
  }
  if (title.includes(query)) {
    return 5
  }
  if (description.includes(query)) {
    return 6
  }
  if (keywords.some((keyword) => keyword.includes(query))) {
    return 7
  }
  if (query.length >= 2
      && (isSubsequence(query, title) || keywords.some((keyword) => isSubsequence(query, keyword)))) {
    return 8
  }
  return null
}

export function rankEntries<T extends PaletteEntry>(entries: T[], rawQuery: string): T[] {
  const query = normalize(rawQuery)
  if (query === '') {
    return entries
  }

  return entries
    .map<RankedEntry<T> | null>((entry) => {
      const score = scoreEntry(entry, query)
      return score === null ? null : { entry, score }
    })
    .filter((ranked): ranked is RankedEntry<T> => ranked !== null)
    .sort((left, right) => {
      if (left.score !== right.score) {
        return left.score - right.score
      }
      return left.entry.title.localeCompare(right.entry.title)
    })
    .map((ranked) => ranked.entry)
}

export interface HighlightSegment {
  text: string
  match: boolean
}

/** Splits visible text around the first case-insensitive occurrence of the query. */
export function highlightSegments(text: string, rawQuery: string): HighlightSegment[] {
  const query = rawQuery.trim()
  if (query === '') {
    return [{ text, match: false }]
  }
  const index = text.toLowerCase().indexOf(query.toLowerCase())
  if (index < 0) {
    return [{ text, match: false }]
  }
  const end = index + query.length
  return [
    { text: text.slice(0, index), match: false },
    { text: text.slice(index, end), match: true },
    { text: text.slice(end), match: false },
  ].filter((segment) => segment.text.length > 0)
}
