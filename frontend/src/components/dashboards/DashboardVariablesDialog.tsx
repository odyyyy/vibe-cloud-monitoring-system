import { useEffect, useState, type FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import type { DashboardVariable, DashboardVariableType } from '../../api/dashboards'
import { useUpdateDashboardMutation } from '../../api/dashboards'
import {
  MAX_VARIABLES_PER_DASHBOARD,
  parseOptionsText,
  validateVariables,
} from '../../features/dashboards/variables'
import { Button } from '../ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog'
import { Input } from '../ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

const NO_DEFAULT = '__none__'

interface VariableDraft {
  name: string
  type: DashboardVariableType
  optionsText: string
  defaultValue: string
}

function toDraft(variable: DashboardVariable): VariableDraft {
  return {
    name: variable.name,
    type: variable.type,
    optionsText: variable.options.join(', '),
    defaultValue: variable.defaultValue,
  }
}

function emptyDraft(): VariableDraft {
  return { name: '', type: 'custom', optionsText: '', defaultValue: '' }
}

interface DashboardVariablesDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  dashboardId: number
  dashboardName: string
  variables: DashboardVariable[]
}

/**
 * Editor for a dashboard's template variable definitions. Only definitions are persisted;
 * the values selected in the variables bar stay client-side.
 */
export function DashboardVariablesDialog({
  open,
  onOpenChange,
  dashboardId,
  dashboardName,
  variables,
}: DashboardVariablesDialogProps) {
  const updateDashboard = useUpdateDashboardMutation(dashboardId)
  const [drafts, setDrafts] = useState<VariableDraft[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      setDrafts(variables.length > 0 ? variables.map(toDraft) : [emptyDraft()])
      setError(null)
    }
    // Seed the draft when the dialog opens; edits must survive background refetches.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const updateDraft = (index: number, patch: Partial<VariableDraft>) => {
    setDrafts((current) => current.map((draft, i) => (i === index ? { ...draft, ...patch } : draft)))
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const next: DashboardVariable[] = drafts.map((draft) => ({
      name: draft.name.trim(),
      type: draft.type,
      options: parseOptionsText(draft.optionsText),
      defaultValue: draft.defaultValue,
    }))

    const validationError = validateVariables(next)
    if (validationError) {
      setError(validationError)
      return
    }

    try {
      await updateDashboard.mutateAsync({ id: dashboardId, data: { name: dashboardName, variables: next } })
      toast.success('Variables saved.')
      onOpenChange(false)
    } catch (submitError) {
      setError(submitError instanceof ApiError ? submitError.message : 'Could not save the variables.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Dashboard variables</DialogTitle>
          <DialogDescription>
            Variables are shared by every widget on this dashboard. Reference one in a widget config as{' '}
            <code className="rounded bg-white/5 px-1 py-0.5 text-xs text-cyan-200">{'${name}'}</code>. Options are static in
            this version.
          </DialogDescription>
        </DialogHeader>

        <form className="mt-5 space-y-4" noValidate onSubmit={handleSubmit}>
          <ul className="space-y-3">
            {drafts.map((draft, index) => {
              const optionValues = parseOptionsText(draft.optionsText)
              const selectOptions =
                draft.defaultValue !== '' && !optionValues.includes(draft.defaultValue)
                  ? [draft.defaultValue, ...optionValues]
                  : optionValues
              return (
                <li
                  key={index}
                  className="grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1fr)_7rem_minmax(0,1.4fr)_minmax(0,1fr)_auto]"
                >
                  <Input
                    aria-label={`Variable ${index + 1} name`}
                    placeholder="env"
                    autoComplete="off"
                    value={draft.name}
                    onChange={(event) => updateDraft(index, { name: event.target.value })}
                  />

                  <Select
                    value={draft.type}
                    onValueChange={(value) => updateDraft(index, { type: value as DashboardVariableType })}
                  >
                    <SelectTrigger aria-label={`Variable ${index + 1} type`}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="custom">Custom</SelectItem>
                      <SelectItem value="query">Query</SelectItem>
                    </SelectContent>
                  </Select>

                  <Input
                    aria-label={`Variable ${index + 1} options`}
                    placeholder="prod, staging"
                    autoComplete="off"
                    value={draft.optionsText}
                    onChange={(event) => updateDraft(index, { optionsText: event.target.value })}
                  />

                  <Select
                    value={draft.defaultValue === '' ? NO_DEFAULT : draft.defaultValue}
                    onValueChange={(value) =>
                      updateDraft(index, { defaultValue: value === NO_DEFAULT ? '' : value })
                    }
                  >
                    <SelectTrigger aria-label={`Variable ${index + 1} default value`}>
                      <SelectValue placeholder="No default" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_DEFAULT}>No default</SelectItem>
                      {selectOptions.map((option) => (
                        <SelectItem key={option} value={option}>
                          {option}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>

                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Remove variable ${index + 1}`}
                    className="text-red-300 hover:text-red-200"
                    onClick={() => setDrafts((current) => current.filter((_, i) => i !== index))}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </li>
              )
            })}
          </ul>

          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={drafts.length >= MAX_VARIABLES_PER_DASHBOARD}
              onClick={() => setDrafts((current) => [...current, emptyDraft()])}
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              Add variable
            </Button>
            <span className="text-xs text-slate-500">
              Names may contain only letters, digits and underscores.
            </span>
          </div>

          {error ? (
            <p role="alert" className="text-sm text-red-400">
              {error}
            </p>
          ) : null}

          <DialogFooter>
            <Button
              type="button"
              variant="secondary"
              onClick={() => onOpenChange(false)}
              disabled={updateDashboard.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={updateDashboard.isPending}>
              {updateDashboard.isPending ? 'Saving…' : 'Save variables'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
