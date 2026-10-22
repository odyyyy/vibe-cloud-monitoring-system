import { describe, expect, it } from 'vitest'
import { API_KEY_PLACEHOLDER, buildAgentDockerCommand, buildAgentDockerCommandDisplay, shellSingleQuote } from './dockerCommand'

describe('shellSingleQuote', () => {
  it('wraps a plain value in single quotes', () => {
    expect(shellSingleQuote('app_123')).toBe("'app_123'")
  })

  it('keeps shell metacharacters literal', () => {
    expect(shellSingleQuote('$HOME `id` \\')).toBe("'$HOME `id` \\'")
  })

  it('terminates, escapes and reopens embedded single quotes', () => {
    expect(shellSingleQuote("a'b")).toBe("'a'\\''b'")
    expect(shellSingleQuote("'; rm -rf /")).toBe("''\\''; rm -rf /'")
  })
})

describe('buildAgentDockerCommand', () => {
  it('builds the exact command with escaped backend URL and API key (single line)', () => {
    const command = buildAgentDockerCommand({
      backendUrl: 'https://cloud.example.com',
      apiKey: 'app_11111111-2222-3333-4444-555555555555',
    })

    expect(command).toBe(
      "docker run -d --name monitoring-agent -e AGENT_BACKEND_URL='https://cloud.example.com' -e AGENT_API_KEY='app_11111111-2222-3333-4444-555555555555' -e AGENT_INTERVAL_SECONDS=15 vibemonitoring/agent:latest"
    )
  })

  it('escapes special characters in the API key', () => {
    const command = buildAgentDockerCommand({
      backendUrl: 'http://localhost:5173',
      apiKey: "app_$(echo pwned)'",
    })

    expect(command).toContain("AGENT_API_KEY='app_$(echo pwned)'\\'''")
  })

  it('falls back to a placeholder when the plaintext key is unavailable', () => {
    const command = buildAgentDockerCommand({
      backendUrl: 'https://cloud.example.com',
      apiKey: null,
    })

    expect(command).toContain(`AGENT_API_KEY=${API_KEY_PLACEHOLDER}`)
    expect(API_KEY_PLACEHOLDER.startsWith('app_')).toBe(false)
  })
})

describe('buildAgentDockerCommandDisplay', () => {
  it('builds the multi-line display command with backslashes', () => {
    const command = buildAgentDockerCommandDisplay({
      backendUrl: 'https://cloud.example.com',
      apiKey: 'app_11111111-2222-3333-4444-555555555555',
    })

    expect(command).toBe(
      [
        'docker run -d \\',
        '  --name monitoring-agent \\',
        "  -e AGENT_BACKEND_URL='https://cloud.example.com' \\",
        "  -e AGENT_API_KEY='app_11111111-2222-3333-4444-555555555555' \\",
        '  -e AGENT_INTERVAL_SECONDS=15 \\',
        '  vibemonitoring/agent:latest',
      ].join('\n'),
    )
  })
})
