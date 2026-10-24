export const MAX_INCIDENT_COMMENT_LENGTH = 2000

export type IncidentCommentSegment =
  | { type: 'text'; value: string }
  | { type: 'strong'; value: string }
  | { type: 'emphasis'; value: string }
  | { type: 'code'; value: string }
  | { type: 'link'; href: string; value: string }
  | { type: 'dashboard'; dashboardId: number; value: string }

/**
 * Validates a comment before it is sent to the API. The backend enforces the same limits and
 * also strips markup, so this is only a fast client-side guard.
 */
export function validateIncidentComment(value: string): string | null {
  const trimmed = value.trim()
  if (trimmed.length === 0) {
    return 'Write a comment before posting.'
  }
  if (trimmed.length > MAX_INCIDENT_COMMENT_LENGTH) {
    return `Comments must be at most ${MAX_INCIDENT_COMMENT_LENGTH} characters.`
  }
  return null
}

/**
 * Markdown-like inline tokens. Only http(s) links are recognized, and dashboard references are
 * resolved against the internal dashboard route. Raw HTML is deliberately left as plain text.
 */
const INLINE_TOKEN =
  /`([^`\n]+)`|\*\*([^*\n]+)\*\*|\*([^*\n]+)\*|\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)|([@/]?dashboard:(\d+))/g

/**
 * Converts comment text into safe renderable segments.
 *
 * The result is rendered as React elements (never `dangerouslySetInnerHTML`), so markup in a
 * comment is displayed as text and cannot execute. `dashboard:123` becomes an internal link to
 * dashboard 123; links open in a new tab.
 */
export function parseIncidentComment(text: string): IncidentCommentSegment[] {
  const segments: IncidentCommentSegment[] = []
  let lastIndex = 0

  for (const match of text.matchAll(INLINE_TOKEN)) {
    const index = match.index ?? 0

    // Do not turn "mydashboard:1" into a link.
    if (match[6] !== undefined) {
      const previous = index > 0 ? text[index - 1] : ''
      if (/[A-Za-z0-9_]/.test(previous)) {
        continue
      }
    }

    if (index > lastIndex) {
      segments.push({ type: 'text', value: text.slice(lastIndex, index) })
    }

    if (match[1] !== undefined) {
      segments.push({ type: 'code', value: match[1] })
    } else if (match[2] !== undefined) {
      segments.push({ type: 'strong', value: match[2] })
    } else if (match[3] !== undefined) {
      segments.push({ type: 'emphasis', value: match[3] })
    } else if (match[4] !== undefined && match[5] !== undefined) {
      segments.push({ type: 'link', href: match[5], value: match[4] })
    } else if (match[7] !== undefined) {
      const dashboardId = Number(match[7])
      if (Number.isSafeInteger(dashboardId) && dashboardId > 0) {
        segments.push({
          type: 'dashboard',
          dashboardId,
          value: `dashboard:${dashboardId}`,
        })
      } else {
        segments.push({ type: 'text', value: match[0] })
      }
    }

    lastIndex = index + match[0].length
  }

  if (lastIndex < text.length) {
    segments.push({ type: 'text', value: text.slice(lastIndex) })
  }
  return segments
}
