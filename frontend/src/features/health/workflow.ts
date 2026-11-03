import type {
  AssertionTypeValue,
  SyntheticHttpMethodValue,
} from './schemas'
import {
  DEFAULT_ASSERTION_TYPE,
  DEFAULT_STEP_METHOD,
  SYNTHETIC_HTTP_METHODS,
} from './schemas'
import type { SyntheticApiStep, SyntheticAssertion } from '../../api/healthchecks'

export interface HeaderDraft {
  id: string
  name: string
  value: string
}

export interface ExtractionDraft {
  id: string
  name: string
  expression: string
}

export interface AssertionDraft {
  id: string
  type: AssertionTypeValue
  operator: string
  value: string
  path: string
}

export interface StepDraft {
  id: string
  name: string
  method: SyntheticHttpMethodValue
  url: string
  body: string
  headers: HeaderDraft[]
  extractions: ExtractionDraft[]
  assertions: AssertionDraft[]
}

let nextDraftId = 1

function draftId(): string {
  nextDraftId += 1
  return `draft-${nextDraftId}`
}

export function emptyHeader(): HeaderDraft {
  return { id: draftId(), name: '', value: '' }
}

export function emptyExtraction(): ExtractionDraft {
  return { id: draftId(), name: '', expression: '' }
}

export function emptyAssertion(): AssertionDraft {
  return { id: draftId(), type: DEFAULT_ASSERTION_TYPE, operator: '==', value: '200', path: '' }
}

export function emptyStep(): StepDraft {
  return {
    id: draftId(),
    name: '',
    method: DEFAULT_STEP_METHOD,
    url: '',
    body: '',
    headers: [],
    extractions: [],
    assertions: [emptyAssertion()],
  }
}

/** Converts a stored workflow to editable drafts, dropping empty placeholders. */
export function fromApiSteps(steps: readonly SyntheticApiStep[] | null | undefined): StepDraft[] {
  if (!steps || steps.length === 0) {
    return []
  }
  return steps.map((step) => ({
    id: draftId(),
    name: step.name,
    method: step.method,
    url: step.url,
    body: step.body ?? '',
    headers: Object.entries(step.headers ?? {}).map(([name, value]) => ({
      id: draftId(),
      name,
      value,
    })),
    extractions: Object.entries(step.extractVariables ?? {}).map(([name, expression]) => ({
      id: draftId(),
      name,
      expression,
    })),
    assertions: (step.assertions ?? []).map((assertion) => ({
      id: draftId(),
      type: assertion.type,
      operator: assertion.operator,
      value: String(assertion.value),
      path: assertion.path ?? '',
    })),
  }))
}

function headersFromDrafts(drafts: readonly HeaderDraft[]): Record<string, string> {
  const headers: Record<string, string> = {}
  for (const draft of drafts) {
    const name = draft.name.trim()
    if (name.length > 0) {
      headers[name] = draft.value
    }
  }
  return headers
}

function extractionsFromDrafts(drafts: readonly ExtractionDraft[]): Record<string, string> {
  const extractions: Record<string, string> = {}
  for (const draft of drafts) {
    const name = draft.name.trim()
    if (name.length > 0) {
      extractions[name] = draft.expression.trim()
    }
  }
  return extractions
}

function assertionsFromDrafts(drafts: readonly AssertionDraft[]): SyntheticAssertion[] {
  return drafts.map((draft) => {
    const assertion: SyntheticAssertion = {
      type: draft.type,
      operator: draft.operator,
      value: draft.type === 'STATUS_CODE' ? Number(draft.value) : draft.value,
    }
    if (draft.type === 'JSON_PATH') {
      assertion.path = draft.path.trim()
    }
    return assertion
  })
}

/** Converts drafts to the API payload shape that the schemas and backend expect. */
export function toApiSteps(drafts: readonly StepDraft[]): SyntheticApiStep[] {
  return drafts.map((draft) => {
    const step: SyntheticApiStep = {
      name: draft.name.trim(),
      method: draft.method,
      url: draft.url.trim(),
      headers: headersFromDrafts(draft.headers),
      extractVariables: extractionsFromDrafts(draft.extractions),
      assertions: assertionsFromDrafts(draft.assertions),
    }
    if (draft.method !== 'GET' && draft.method !== 'HEAD' && draft.body.length > 0) {
      step.body = draft.body
    }
    return step
  })
}

export function methodForbidsBody(method: SyntheticHttpMethodValue): boolean {
  return method === 'GET' || method === 'HEAD'
}

export function operatorsForAssertion(type: AssertionTypeValue): readonly string[] {
  return type === 'STATUS_CODE'
    ? ['==', '!=']
    : type === 'BODY_CONTAINS'
      ? ['contains', 'not_contains']
      : ['==', '!=', 'contains', 'not_contains']
}

export function defaultOperatorFor(type: AssertionTypeValue): string {
  return operatorsForAssertion(type)[0]
}

export function isKnownMethod(value: string): value is SyntheticHttpMethodValue {
  return (SYNTHETIC_HTTP_METHODS as readonly string[]).includes(value)
}
