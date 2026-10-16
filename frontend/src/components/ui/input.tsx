import * as React from 'react'
import { cn } from '../../lib/utils'

export type InputProps = React.InputHTMLAttributes<HTMLInputElement>

const Input = React.forwardRef<HTMLInputElement, InputProps>(({ className, type, ...props }, ref) => (
  <input
    type={type}
    ref={ref}
    className={cn(
      'flex h-10 w-full rounded-md border border-white/10 bg-[#080b14] px-3 py-2 text-sm text-slate-100 transition-colors',
      'placeholder:text-slate-500 focus-visible:border-cyan-400/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/50',
      'disabled:cursor-not-allowed disabled:opacity-50',
      'aria-[invalid=true]:border-red-500/60 aria-[invalid=true]:focus-visible:ring-red-500/40',
      className,
    )}
    {...props}
  />
))
Input.displayName = 'Input'

export { Input }
