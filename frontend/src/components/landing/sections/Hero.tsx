import React from 'react'
import { Link } from 'react-router-dom'
import { IconArrow, IconCheck } from '../../shared/icons'
import { LivePanel } from '../components/LivePanel'

export function Hero() {
  return (
    <section id="top" className="relative overflow-hidden pt-32 pb-24">
      <div className="bg-grid mask-radial absolute inset-0" />
      <div className="orb left-[-10%] top-[-10%] h-[420px] w-[420px] bg-cyan-500/30" />
      <div className="orb right-[-12%] top-[20%] h-[460px] w-[460px] bg-violet-600/30" />
      <div className="orb bottom-[-20%] left-[40%] h-[380px] w-[380px] bg-lime-400/10" />

      <div className="pointer-events-none absolute inset-x-0 top-16 select-none text-center">
        <span className="outlined-text font-display text-[22vw] font-bold leading-none opacity-60">OBSERVE</span>
      </div>

      <div className="relative mx-auto grid max-w-7xl items-center gap-16 px-6 lg:grid-cols-2">
        <div>
          <span className="section-tag mono">// Observability for modern clouds</span>
          <h1 className="font-display mt-6 text-5xl font-bold leading-[1.04] tracking-tight text-slate-100 sm:text-6xl">
            See <span className="text-gradient">every signal.</span>
            <br />
            Ship with confidence.
          </h1>
          <p className="mt-6 max-w-xl text-lg leading-relaxed text-slate-400">
            CloudMonitor brings metrics, logs and service health into one tenant-safe platform — so your team spots
            problems before your users do.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <Link
              to="/register"
              className="group inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-cyan-400 to-violet-500 px-6 py-3 text-sm font-semibold text-[#05070d] transition-transform hover:scale-[1.03]"
            >
              Start observing free
              <IconArrow className="h-4 w-4 transition-transform group-hover:translate-x-1" />
            </Link>
            <a
              href="#flow"
              className="inline-flex items-center gap-2 rounded-full border border-white/10 px-6 py-3 text-sm font-medium text-slate-200 transition-colors hover:border-cyan-400/40 hover:text-cyan-300"
            >
              See how it works
            </a>
          </div>

          <div className="mt-10 flex flex-wrap gap-2">
            {['SOC 2 ready', 'Tenant isolation by design', 'OpenTelemetry native'].map((t) => (
              <span key={t} className="chip mono">
                <IconCheck className="h-3 w-3 text-cyan-300" />
                {t}
              </span>
            ))}
          </div>
        </div>

        <LivePanel />
      </div>
    </section>
  )
}