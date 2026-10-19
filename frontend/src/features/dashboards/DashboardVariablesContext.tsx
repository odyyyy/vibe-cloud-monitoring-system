import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { DashboardVariable } from '../../api/dashboards'
import { defaultVariableValues } from './variables'

interface DashboardVariablesContextValue {
  values: Record<string, string>
  setValue: (name: string, value: string) => void
}

const EMPTY_VALUES: Record<string, string> = {}

const DashboardVariablesContext = createContext<DashboardVariablesContextValue | null>(null)

interface DashboardVariablesProviderProps {
  variables: readonly DashboardVariable[]
  children: ReactNode
}

/**
 * Holds the currently selected template variable values for one dashboard. Values are pure
 * client-side state: only definitions are persisted. Definitions are compared by content so
 * a refetch after e.g. a layout save does not discard the user's selection.
 */
export function DashboardVariablesProvider({ variables, children }: DashboardVariablesProviderProps) {
  const signature = useMemo(
    () => JSON.stringify(variables.map((variable) => [variable.name, variable.options, variable.defaultValue])),
    [variables],
  )
  const initialValues = useMemo(
    () => defaultVariableValues(variables),
    // Definitions are stable by content; `variables` identity changes on every refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [signature],
  )
  const [values, setValues] = useState(initialValues)

  useEffect(() => {
    setValues(initialValues)
  }, [initialValues])

  const setValue = useCallback((name: string, value: string) => {
    setValues((current) => (current[name] === value ? current : { ...current, [name]: value }))
  }, [])

  const contextValue = useMemo(() => ({ values, setValue }), [values, setValue])

  return <DashboardVariablesContext.Provider value={contextValue}>{children}</DashboardVariablesContext.Provider>
}

/** Full context (values plus setter), or `null` when rendered outside a provider. */
export function useDashboardVariables(): DashboardVariablesContextValue | null {
  return useContext(DashboardVariablesContext)
}

/**
 * Selected values for widget interpolation. Returns an empty map outside a provider, so widgets
 * rendered without a variables bar (e.g. on the overview page) keep their raw config.
 */
export function useDashboardVariableValues(): Readonly<Record<string, string>> {
  return useContext(DashboardVariablesContext)?.values ?? EMPTY_VALUES
}
