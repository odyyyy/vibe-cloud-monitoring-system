export const AGENT_IMAGE = 'vibemonitoring/agent:latest'

/** Placeholder used when the plaintext API key is no longer available (it is only shown once). */
export const API_KEY_PLACEHOLDER = 'REPLACE_WITH_YOUR_API_KEY'

/**
 * Wraps a value in POSIX single quotes so it can be pasted into a shell safely.
 * Embedded single quotes are terminated, escaped and reopened (`'` -> `'\''`), which keeps
 * every other character (including `$`, backticks and backslashes) literal.
 */
export function shellSingleQuote(value: string): string {
  return `'${value.replace(/'/g, "'\\''")}'`
}

export interface AgentDockerCommandOptions {
  backendUrl: string
  apiKey: string | null
}

/**
 * Builds the `docker run` command shown in the onboarding wizard. Both the backend URL and the
 * API key are shell-escaped; a `null` key falls back to a placeholder the user must replace.
 */
export function buildAgentDockerCommand({ backendUrl, apiKey }: AgentDockerCommandOptions): string {
  const keyValue = apiKey ? shellSingleQuote(apiKey) : API_KEY_PLACEHOLDER
  return [
    'docker run -d \\',
    '  --name monitoring-agent \\',
    `  -e AGENT_BACKEND_URL=${shellSingleQuote(backendUrl)} \\`,
    `  -e AGENT_API_KEY=${keyValue} \\`,
    '  -e AGENT_INTERVAL_SECONDS=15 \\',
    `  ${AGENT_IMAGE}`,
  ].join('\n')
}

/** Derives the backend origin agents should report to from the current page origin. */
export function resolveBackendUrl(): string {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.location.origin
}
