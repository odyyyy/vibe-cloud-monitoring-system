import type { DashboardVariable } from '../../api/dashboards'
import { useDashboardVariables } from '../../features/dashboards/DashboardVariablesContext'
import { defaultVariableValues } from '../../features/dashboards/variables'
import { Label } from '../ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface DashboardVariablesBarProps {
  variables: DashboardVariable[]
}

/**
 * Horizontal bar of variable dropdowns shown at the top of a dashboard. Changing a value
 * updates the shared variable state, which re-runs every widget query that references it.
 */
export function DashboardVariablesBar({ variables }: DashboardVariablesBarProps) {
  const context = useDashboardVariables()
  const values = context?.values ?? defaultVariableValues(variables)

  if (variables.length === 0) {
    return null
  }

  return (
    <div className="flex flex-wrap items-end gap-4 rounded-2xl border border-white/10 bg-panel/40 px-4 py-3">
      {variables.map((variable) => {
        const selected = values[variable.name] ?? ''
        const options =
          selected === '' || variable.options.includes(selected) ? variable.options : [selected, ...variable.options]
        return (
          <div key={variable.name} className="flex flex-col gap-1.5">
            <Label htmlFor={`dashboard-variable-${variable.name}`} className="text-xs text-slate-400">
              {variable.name}
            </Label>
            <Select
              value={selected}
              onValueChange={(value) => context?.setValue(variable.name, value)}
              disabled={context === null}
            >
              <SelectTrigger id={`dashboard-variable-${variable.name}`} className="h-9 w-44">
                <SelectValue placeholder="Select value" />
              </SelectTrigger>
              <SelectContent>
                {options.map((option) => (
                  <SelectItem key={option} value={option}>
                    {option}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )
      })}
    </div>
  )
}
