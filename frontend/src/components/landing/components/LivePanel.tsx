import React from 'react'
import { useCountUp } from '../hooks/useCountUp'
import { Sparkline } from './Sparkline'
import { landingData } from '../data'

export function LivePanel() {
  const cpu = useCountUp(42.8, 1600, 1)
  const lat = useCountUp(182, 1600, 0)
  const thr = useCountUp(1400, 1600, 0)
  const displayedMetrics = [
    { ...landingData.metrics[0], value: `${cpu.toFixed(1)}%` },
    { ...landingData.metrics[1], value: `${Math.round(lat)}ms` },
    { ...landingData.metrics[2], value: `${Math.round(thr)}` },
  ]

  return (
    <div className="tilt relative">
      <div className="gradient-border rounded-2xl p-5 shadow-2xl shadow-cyan-500/10">
        <div className="scanline" />
        <div className="flex items-center justify-between border-b border-white/5 pb-4">
          <div className="flex items-center gap-2">
            <span className="glow-dot h-2 w-2 rounded-full bg-emerald-400 text-emerald-400" />
            <span className="mono text-xs uppercase tracking-[0.2em] text-slate-400">Live</span>
          </div>
          <div className="mono text-xs text-slate-500">tenant / acme-prod</div>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          {displayedMetrics.map((metric) => (
            <div key={metric.label} className="rounded-xl border border-white/5 bg-white/[0.02] p-3">
              <div className="mono truncate text-[10px] uppercase tracking-wider text-slate-500">{metric.label}</div>
              <div className="font-display mt-1 text-xl font-semibold text-slate-100">{metric.value}</div>
              <div className="mt-2">
                <Sparkline values={metric.series} stroke={metric.color} gradientId={metric.gradient} />
              </div>
            </div>
          ))}
        </div>

        <div className="mt-4 rounded-xl border border-white/5 bg-[#05070d]/70 p-3">
          <div className="mono mb-2 flex items-center justify-between text-[10px] uppercase tracking-wider text-slate-500">
            <span>stream</span>
            <span>tail -f /var/log/app</span>
          </div>
          <div className="relative h-28 overflow-hidden">
            <div className="absolute inset-x-0 top-0 h-8 bg-gradient-to-b from-[#05070d] to-transparent" />
            <div className="log-stream space-y-1.5">
              {[...landingData.logLines, ...landingData.logLines].map((line, index) => (
                <div key={`${line.t}-${index}`} className="mono flex gap-2 text-[10.5px] leading-relaxed">
                  <span className="text-slate-600">{line.t}</span>
                  <span
                    className={
                      line.level === 'ERROR'
                        ? 'text-rose-400'
                        : line.level === 'WARN'
                        ? 'text-amber-300'
                        : line.level === 'DEBUG'
                        ? 'text-slate-500'
                        : 'text-cyan-300'
                    }
                  >
                    {line.level.padEnd(5, ' ')}
                  </span>
                  <span className="text-violet-300">{line.service}</span>
                  <span className="truncate text-slate-400">{line.msg}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="float-slow absolute -right-6 -top-6 hidden rounded-xl border border-white/10 bg-[#0b101b]/90 px-4 py-3 shadow-xl lg:block">
        <div className="mono text-[10px] uppercase tracking-wider text-slate-500">p99</div>
        <div className="font-display text-lg font-semibold text-lime-300">182ms</div>
      </div>
      <div className="float-slower absolute -bottom-6 -left-6 hidden rounded-xl border border-white/10 bg-[#0b101b]/90 px-4 py-3 shadow-xl lg:block">
        <div className="mono text-[10px] uppercase tracking-wider text-slate-500">agents</div>
        <div className="font-display text-lg font-semibold text-cyan-300">3 connected</div>
      </div>
    </div>
  )
}