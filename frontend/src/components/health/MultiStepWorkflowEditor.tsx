import { Plus, Trash2 } from 'lucide-react'
import type { StepDraft } from '../../features/health/workflow'
import {
  emptyAssertion,
  emptyExtraction,
  emptyHeader,
  emptyStep,
  methodForbidsBody,
  operatorsForAssertion,
  defaultOperatorFor,
} from '../../features/health/workflow'
import {
  ASSERTION_TYPES,
  MAX_ASSERTIONS_PER_STEP,
  MAX_EXTRACTIONS_PER_STEP,
  MAX_HEADERS_PER_STEP,
  MAX_STEPS,
  SYNTHETIC_HTTP_METHODS,
} from '../../features/health/schemas'
import { Button } from '../ui/button'
import { Input } from '../ui/input'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface MultiStepWorkflowEditorProps {
  steps: StepDraft[]
  onChange: (steps: StepDraft[]) => void
}

/**
 * Builder for a multi-step API workflow. Editing happens on drafts with stable ids; the parent
 * converts them to the API shape and validates them with the shared zod schema before submit.
 * The backend remains the security boundary for URLs, headers and extraction expressions.
 */
export function MultiStepWorkflowEditor({ steps, onChange }: MultiStepWorkflowEditorProps) {
  const updateStep = (stepId: string, patch: Partial<StepDraft>) => {
    onChange(steps.map((step) => (step.id === stepId ? { ...step, ...patch } : step)))
  }

  const updateHeader = (stepId: string, headerId: string, patch: Partial<StepDraft['headers'][number]>) => {
    onChange(
      steps.map((step) =>
        step.id === stepId
          ? {
              ...step,
              headers: step.headers.map((header) =>
                header.id === headerId ? { ...header, ...patch } : header,
              ),
            }
          : step,
      ),
    )
  }

  const updateExtraction = (
    stepId: string,
    extractionId: string,
    patch: Partial<StepDraft['extractions'][number]>,
  ) => {
    onChange(
      steps.map((step) =>
        step.id === stepId
          ? {
              ...step,
              extractions: step.extractions.map((extraction) =>
                extraction.id === extractionId ? { ...extraction, ...patch } : extraction,
              ),
            }
          : step,
      ),
    )
  }

  const updateAssertion = (
    stepId: string,
    assertionId: string,
    patch: Partial<StepDraft['assertions'][number]>,
  ) => {
    onChange(
      steps.map((step) =>
        step.id === stepId
          ? {
              ...step,
              assertions: step.assertions.map((assertion) =>
                assertion.id === assertionId ? { ...assertion, ...patch } : assertion,
              ),
            }
          : step,
      ),
    )
  }

  const addStep = () => {
    if (steps.length < MAX_STEPS) {
      onChange([...steps, emptyStep()])
    }
  }

  const removeStep = (stepId: string) => {
    if (steps.length <= 1) {
      return
    }
    onChange(steps.filter((step) => step.id !== stepId))
  }

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2.5 text-xs text-slate-400">
        Steps run in order. Extract a value in one step (for example <code>jsonpath:$.access_token</code>)
        and use it in later steps as <code>{'{{token}}'}</code> in the URL, headers or body.
        Redirects are never followed and every URL is checked for SSRF safety.
      </div>

      {steps.map((step, index) => {
        const forbidsBody = methodForbidsBody(step.method)
        return (
          <fieldset
            key={step.id}
            className="space-y-4 rounded-xl border border-white/10 bg-white/[0.02] p-4"
          >
            <div className="flex items-center justify-between gap-2">
              <legend className="font-display text-sm font-semibold text-slate-100">
                Step {index + 1}
              </legend>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={`Remove step ${index + 1}`}
                disabled={steps.length <= 1}
                onClick={() => removeStep(step.id)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>

            <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
              <div className="space-y-1.5">
                <Label htmlFor={`${step.id}-name`}>Step name</Label>
                <Input
                  id={`${step.id}-name`}
                  value={step.name}
                  placeholder="Login"
                  onChange={(event) => updateStep(step.id, { name: event.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor={`${step.id}-method`}>Method</Label>
                <Select
                  value={step.method}
                  onValueChange={(value) =>
                    updateStep(step.id, { method: value as StepDraft['method'] })
                  }
                >
                  <SelectTrigger id={`${step.id}-method`} className="w-32">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SYNTHETIC_HTTP_METHODS.map((method) => (
                      <SelectItem key={method} value={method}>
                        {method}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor={`${step.id}-url`}>URL</Label>
              <Input
                id={`${step.id}-url`}
                value={step.url}
                placeholder="https://api.example.com/login"
                onChange={(event) => updateStep(step.id, { url: event.target.value })}
              />
              <p className="text-xs text-slate-500">
                Variables are allowed in the path and query, not in the scheme or host.
              </p>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Headers</Label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={step.headers.length >= MAX_HEADERS_PER_STEP}
                  onClick={() => updateStep(step.id, { headers: [...step.headers, emptyHeader()] })}
                >
                  <Plus className="mr-1 h-4 w-4" aria-hidden="true" />
                  Add header
                </Button>
              </div>
              {step.headers.length === 0 ? (
                <p className="text-xs text-slate-500">No extra headers.</p>
              ) : (
                <div className="space-y-2">
                  {step.headers.map((header) => (
                    <div key={header.id} className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
                      <Input
                        aria-label="Header name"
                        value={header.name}
                        placeholder="Authorization"
                        onChange={(event) =>
                          updateHeader(step.id, header.id, { name: event.target.value })
                        }
                      />
                      <Input
                        aria-label="Header value"
                        value={header.value}
                        placeholder="Bearer {{token}}"
                        onChange={(event) =>
                          updateHeader(step.id, header.id, { value: event.target.value })
                        }
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label="Remove header"
                        onClick={() =>
                          updateStep(step.id, {
                            headers: step.headers.filter((entry) => entry.id !== header.id),
                          })
                        }
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor={`${step.id}-body`}>Body</Label>
              <textarea
                id={`${step.id}-body`}
                rows={3}
                value={step.body}
                disabled={forbidsBody}
                placeholder={forbidsBody ? 'GET and HEAD requests do not send a body.' : '{"user":"demo"}'}
                onChange={(event) => updateStep(step.id, { body: event.target.value })}
                className="w-full rounded-md border border-white/10 bg-white/[0.03] px-3 py-2 font-mono text-sm text-slate-100 placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60 disabled:cursor-not-allowed disabled:opacity-60"
              />
              <p className="text-xs text-slate-500">
                Sent as application/json unless a Content-Type header is set.
              </p>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Extract variables</Label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={step.extractions.length >= MAX_EXTRACTIONS_PER_STEP}
                  onClick={() =>
                    updateStep(step.id, { extractions: [...step.extractions, emptyExtraction()] })
                  }
                >
                  <Plus className="mr-1 h-4 w-4" aria-hidden="true" />
                  Add extraction
                </Button>
              </div>
              {step.extractions.length === 0 ? (
                <p className="text-xs text-slate-500">
                  Nothing extracted. Use <code>jsonpath:$.field</code> or <code>regex:capture</code>.
                </p>
              ) : (
                <div className="space-y-2">
                  {step.extractions.map((extraction) => (
                    <div key={extraction.id} className="grid gap-2 sm:grid-cols-[1fr_1.4fr_auto]">
                      <Input
                        aria-label="Variable name"
                        value={extraction.name}
                        placeholder="token"
                        onChange={(event) =>
                          updateExtraction(step.id, extraction.id, { name: event.target.value })
                        }
                      />
                      <Input
                        aria-label="Extraction expression"
                        value={extraction.expression}
                        placeholder="jsonpath:$.access_token"
                        onChange={(event) =>
                          updateExtraction(step.id, extraction.id, { expression: event.target.value })
                        }
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label="Remove extraction"
                        onClick={() =>
                          updateStep(step.id, {
                            extractions: step.extractions.filter(
                              (entry) => entry.id !== extraction.id,
                            ),
                          })
                        }
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Assertions</Label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={step.assertions.length >= MAX_ASSERTIONS_PER_STEP}
                  onClick={() =>
                    updateStep(step.id, { assertions: [...step.assertions, emptyAssertion()] })
                  }
                >
                  <Plus className="mr-1 h-4 w-4" aria-hidden="true" />
                  Add assertion
                </Button>
              </div>
              {step.assertions.length === 0 ? (
                <p className="text-xs text-slate-500">
                  No assertions; a 2xx status is required by default.
                </p>
              ) : (
                <div className="space-y-2">
                  {step.assertions.map((assertion) => (
                    <div key={assertion.id} className="space-y-2 rounded-lg border border-white/10 p-2">
                      <div className="grid gap-2 sm:grid-cols-[1.2fr_1fr_auto]">
                        <div className="space-y-1">
                          <Label htmlFor={`${assertion.id}-type`} className="text-xs text-slate-400">
                            Type
                          </Label>
                          <Select
                            value={assertion.type}
                            onValueChange={(value) => {
                              const type = value as StepDraft['assertions'][number]['type']
                              updateAssertion(step.id, assertion.id, {
                                type,
                                operator: defaultOperatorFor(type),
                                value: type === 'STATUS_CODE' ? '200' : '',
                              })
                            }}
                          >
                            <SelectTrigger id={`${assertion.id}-type`}>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {ASSERTION_TYPES.map((type) => (
                                <SelectItem key={type} value={type}>
                                  {type}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                        <div className="space-y-1">
                          <Label htmlFor={`${assertion.id}-operator`} className="text-xs text-slate-400">
                            Operator
                          </Label>
                          <Select
                            value={assertion.operator}
                            onValueChange={(value) =>
                              updateAssertion(step.id, assertion.id, { operator: value })
                            }
                          >
                            <SelectTrigger id={`${assertion.id}-operator`}>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {operatorsForAssertion(assertion.type).map((operator) => (
                                <SelectItem key={operator} value={operator}>
                                  {operator}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                        <div className="flex items-end">
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label="Remove assertion"
                            onClick={() =>
                              updateStep(step.id, {
                                assertions: step.assertions.filter(
                                  (entry) => entry.id !== assertion.id,
                                ),
                              })
                            }
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                      {assertion.type === 'JSON_PATH' ? (
                        <div className="space-y-1">
                          <Label htmlFor={`${assertion.id}-path`} className="text-xs text-slate-400">
                            JSON path
                          </Label>
                          <Input
                            id={`${assertion.id}-path`}
                            value={assertion.path}
                            placeholder="$.status"
                            onChange={(event) =>
                              updateAssertion(step.id, assertion.id, { path: event.target.value })
                            }
                          />
                        </div>
                      ) : null}
                      <div className="space-y-1">
                        <Label htmlFor={`${assertion.id}-value`} className="text-xs text-slate-400">
                          Expected value
                        </Label>
                        <Input
                          id={`${assertion.id}-value`}
                          type={assertion.type === 'STATUS_CODE' ? 'number' : 'text'}
                          value={assertion.value}
                          placeholder={assertion.type === 'STATUS_CODE' ? '200' : 'ok'}
                          onChange={(event) =>
                            updateAssertion(step.id, assertion.id, { value: event.target.value })
                          }
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </fieldset>
        )
      })}

      <Button type="button" variant="secondary" disabled={steps.length >= MAX_STEPS} onClick={addStep}>
        <Plus className="mr-1 h-4 w-4" aria-hidden="true" />
        Add step ({steps.length}/{MAX_STEPS})
      </Button>
    </div>
  )
}
