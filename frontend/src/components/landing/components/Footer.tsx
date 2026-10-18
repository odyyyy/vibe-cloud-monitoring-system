import React from 'react'
import { Logo } from '../../shared/icons'

export function Footer() {
  const columns = [
    { title: 'Product', links: ['Metrics', 'Logs', 'Dashboards', 'Agents', 'Alerts'] },
    { title: 'Developers', links: ['Documentation', 'API reference', 'OpenTelemetry', 'Status'] },
    { title: 'Company', links: ['About', 'Security', 'Careers', 'Contact'] },
  ]

  return (
    <footer className="relative border-t border-white/5 bg-canvas py-14">
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-10 md:grid-cols-[1.4fr_repeat(3,1fr)]">
          <div>
            <div className="flex items-center gap-2.5">
              <Logo />
              <span className="font-display text-lg font-semibold text-slate-100">
                Vibe<span className="text-cyan-300">Monitor</span>
              </span>
            </div>
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-slate-500">
              A dependable cloud observability platform. Reliability and tenant isolation first, polish second.
            </p>
          </div>

          {columns.map((column) => (
            <div key={column.title}>
              <h4 className="mono text-xs uppercase tracking-[0.2em] text-slate-500">{column.title}</h4>
              <ul className="mt-4 space-y-2.5">
                {column.links.map((link) => (
                  <li key={link}>
                    <a href="#top" className="text-sm text-slate-400 transition-colors hover:text-cyan-300">
                      {link}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-white/5 pt-8 sm:flex-row">
          <p className="mono text-xs text-slate-600">© 2026 VibeMonitor. All signals reserved.</p>
          <div className="mono flex items-center gap-2 text-xs text-slate-600">
            <span className="glow-dot h-1.5 w-1.5 rounded-full bg-emerald-400 text-emerald-400" />
            all systems operational
          </div>
        </div>
      </div>
    </footer>
  )
}