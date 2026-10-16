import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default: 'border-white/10 bg-white/5 text-slate-300',
        info: 'border-cyan-400/30 bg-cyan-400/10 text-cyan-200',
        success: 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200',
        warning: 'border-amber-400/30 bg-amber-400/10 text-amber-200',
        danger: 'border-red-500/30 bg-red-500/10 text-red-200',
        muted: 'border-white/10 bg-white/[0.03] text-slate-400',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}

export { Badge, badgeVariants }
