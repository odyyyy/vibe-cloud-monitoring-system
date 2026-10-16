import * as React from 'react'
import type { LucideIcon } from 'lucide-react'
import { Eye, EyeOff, Lock } from 'lucide-react'
import { cn } from '../../lib/utils'
import { Input, type InputProps } from '../ui/input'
import { Label } from '../ui/label'

export interface FormFieldProps extends InputProps {
  label: string
  icon: LucideIcon
  error?: string
  hint?: React.ReactNode
  trailing?: React.ReactNode
}

export const FormField = React.forwardRef<HTMLInputElement, FormFieldProps>(
  ({ label, icon: Icon, error, hint, trailing, id, className, ...props }, ref) => {
    const fieldId = id ?? props.name

    return (
      <div className="space-y-1.5">
        <Label htmlFor={fieldId}>{label}</Label>
        <div className="relative">
          <Icon
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500"
            aria-hidden="true"
          />
          <Input
            ref={ref}
            id={fieldId}
            aria-invalid={Boolean(error)}
            className={cn('pl-10', trailing ? 'pr-11' : undefined, className)}
            {...props}
          />
          {trailing ? <div className="absolute right-1.5 top-1/2 -translate-y-1/2">{trailing}</div> : null}
        </div>
        {error ? (
          <p className="text-xs text-red-400">{error}</p>
        ) : hint ? (
          <div className="text-xs text-slate-500">{hint}</div>
        ) : null}
      </div>
    )
  },
)
FormField.displayName = 'FormField'

export type PasswordFieldProps = Omit<FormFieldProps, 'icon' | 'trailing' | 'type'>

export const PasswordField = React.forwardRef<HTMLInputElement, PasswordFieldProps>(
  ({ label, error, hint, ...props }, ref) => {
    const [visible, setVisible] = React.useState(false)

    return (
      <FormField
        ref={ref}
        label={label}
        error={error}
        hint={hint}
        icon={Lock}
        type={visible ? 'text' : 'password'}
        {...props}
        trailing={
          <button
            type="button"
            onClick={() => setVisible((value) => !value)}
            aria-label={visible ? 'Hide password' : 'Show password'}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-500 transition-colors hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50"
          >
            {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        }
      />
    )
  },
)
PasswordField.displayName = 'PasswordField'
