import { describe, expect, it } from 'vitest'
import { emptyStep, fromApiSteps, toApiSteps } from './workflow'

describe('workflow drafts', () => {
  it('round-trips an API workflow through editable drafts', () => {
    const apiSteps = [
      {
        name: 'Login',
        method: 'POST' as const,
        url: 'https://api.example.com/login',
        headers: { 'Content-Type': 'application/json' },
        body: '{"user":"demo"}',
        extractVariables: { token: 'jsonpath:$.access_token' },
        assertions: [{ type: 'STATUS_CODE' as const, operator: '==', value: 200 }],
      },
    ]

    const drafts = fromApiSteps(apiSteps)
    const restored = toApiSteps(drafts)

    expect(restored).toEqual(apiSteps)
  })

  it('drops an empty body for GET and HEAD steps', () => {
    const step = {
      ...emptyStep(),
      name: 'Ping',
      method: 'GET' as const,
      url: 'https://api.example.com/ping',
      body: 'should be ignored',
    }

    const [apiStep] = toApiSteps([step])

    expect(apiStep.body).toBeUndefined()
  })

  it('ignores header and extraction rows with blank names', () => {
    const step = {
      ...emptyStep(),
      name: 'Login',
      method: 'POST' as const,
      url: 'https://api.example.com/login',
      headers: [{ id: 'h1', name: '  ', value: 'x' }],
      extractions: [{ id: 'e1', name: '', expression: 'jsonpath:$.token' }],
    }

    const [apiStep] = toApiSteps([step])

    expect(apiStep.headers).toEqual({})
    expect(apiStep.extractVariables).toEqual({})
  })

  it('coerces status code assertion values to numbers', () => {
    const step = {
      ...emptyStep(),
      name: 'Check',
      method: 'GET' as const,
      url: 'https://api.example.com/health',
      assertions: [{ id: 'a1', type: 'STATUS_CODE' as const, operator: '==', value: '204', path: '' }],
    }

    const [apiStep] = toApiSteps([step])

    expect(apiStep.assertions?.[0]?.value).toBe(204)
  })
})
