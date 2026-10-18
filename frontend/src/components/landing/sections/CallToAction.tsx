import { Link } from 'react-router-dom'
import { IconArrow } from '../../shared/icons'

export function CallToAction() {
  return (
    <section id="cta" className="relative overflow-hidden py-24">
      <div className="bg-grid mask-radial absolute inset-0 opacity-70" />
      <div className="orb left-1/2 top-1/2 h-[420px] w-[720px] -translate-x-1/2 -translate-y-1/2 bg-cyan-500/15" />
      <div className="relative mx-auto max-w-3xl px-6 text-center">
        <span className="section-tag mono justify-center">// Get started</span>
        <h2 className="font-display mt-6 text-4xl font-bold leading-tight tracking-tight text-slate-100 sm:text-5xl">
          Stop flying <span className="text-gradient">blind.</span>
        </h2>
        <p className="mx-auto mt-4 max-w-xl text-lg text-slate-400">
          Create your first project, deploy the agent, and see real telemetry in minutes. No credit card required.
        </p>

        <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Link
            to="/register"
            className="group inline-flex items-center justify-center gap-2 rounded-full bg-gradient-to-r from-cyan-400 to-violet-500 px-6 py-3 text-sm font-semibold text-canvas transition-transform hover:scale-[1.03]"
          >
            Create free account
            <IconArrow className="h-4 w-4 transition-transform group-hover:translate-x-1" />
          </Link>
          <Link
            to="/login"
            className="inline-flex items-center justify-center gap-2 rounded-full border border-white/10 px-6 py-3 text-sm font-medium text-slate-200 transition-colors hover:border-cyan-400/40 hover:text-cyan-300"
          >
            Sign in
          </Link>
        </div>

        <p className="mono mt-5 text-xs text-slate-600">14-day trial · tenant isolation included · cancel anytime</p>
      </div>
    </section>
  )
}
