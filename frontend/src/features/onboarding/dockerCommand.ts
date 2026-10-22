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
 * Builds the `docker run` command for copying (single line, no backslashes).
 */
export function buildAgentDockerCommand({ backendUrl, apiKey }: AgentDockerCommandOptions): string {
  const keyValue = apiKey ? shellSingleQuote(apiKey) : API_KEY_PLACEHOLDER
  return [
    'docker run -d',
    '--name monitoring-agent',
    `-e AGENT_BACKEND_URL=${shellSingleQuote(backendUrl)}`,
    `-e AGENT_API_KEY=${keyValue}`,
    '-e AGENT_INTERVAL_SECONDS=15',
    AGENT_IMAGE,
  ].join(' ')
}

/**
 * Builds the `docker run` command for display (multi-line with backslashes for readability).
 */
export function buildAgentDockerCommandDisplay({ backendUrl, apiKey }: AgentDockerCommandOptions): string {
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
  const origin = window.location.origin
  try {
    const url = new URL(origin)
    // If frontend is on localhost, use host.docker.internal for Docker agent to reach host
    if (url.hostname === 'localhost' || url.hostname === '127.0.0.1') {
      return `http://host.docker.internal:8080`
    }
    // Otherwise use the same host but backend port (8080)
    url.port = '8080'
    return url.toString()
  } catch {
    return origin
  }
}
