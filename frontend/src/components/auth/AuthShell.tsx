import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from '../shared/icons'

interface AuthShellProps {
  title: string
  subtitle?: string
  children: ReactNode
  footer?: ReactNode
}

export function AuthShell({ title, subtitle, children, footer }: AuthShellProps) {
  return (
    <div className="noise relative min-h-screen bg-canvas">
      <div className="bg-grid pointer-events-none absolute inset-0 opacity-40" aria-hidden="true" />
      <div className="orb left-[-10%] top-[-10%] h-[360px] w-[360px] bg-cyan-500/25" />
      <div className="orb bottom-[-12%] right-[-10%] h-[420px] w-[420px] bg-violet-600/25" />

      <div className="relative mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-12">
        <Link to="/" className="mb-8 flex items-center gap-2.5 self-center">
          <Logo />
          <span className="font-display text-lg font-semibold tracking-tight text-slate-100">
            Vibe<span className="text-cyan-300">Monitor</span>
          </span>
        </Link>

        <div className="gradient-border relative overflow-hidden rounded-2xl p-6 sm:p-8">
          <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-cyan-300/60 to-transparent" />
          <header className="mb-6 space-y-1.5">
            <h1 className="font-display text-2xl font-semibold text-slate-100">{title}</h1>
            {subtitle ? <p className="text-sm text-slate-400">{subtitle}</p> : null}
          </header>

          {children}
        </div>

        {footer ? <div className="mt-6 text-center text-sm text-slate-400">{footer}</div> : null}
      </div>
    </div>
  )
}
