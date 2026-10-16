import React from 'react'
import type { ComponentType } from 'react'
import { SectionHeading } from '../components/SectionHeading'
import { Sparkline } from '../components/Sparkline'
import { useInView } from '../hooks/useInView'
import {
  IconPulse,
  IconLogs,
  IconChart,
  IconShield,
  IconBolt,
  IconLayers,
} from '../../shared/icons'

type LandingIconProps = { className?: string }
type LandingIcon = ComponentType<LandingIconProps>

type Accent = {
  text: string
  iconBox: string
  iconGlow: string
  line: string
  rgb: string
}

type VisualKind = 'pulse' | 'logs' | 'widgets' | 'perimeter' | 'limits' | 'stack'

type Feature = {
  icon: LandingIcon
  title: string
  body: string
  index: string
  accent: Accent
  visual: VisualKind
}

const cyanAccent: Accent = {
  text: 'text-cyan-300',
  iconBox: 'border-cyan-400/25 bg-cyan-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(34,211,238,0.55)]',
  line: 'from-transparent via-cyan-400/70 to-transparent',
  rgb: '34, 211, 238',
}

const violetAccent: Accent = {
  text: 'text-violet-300',
  iconBox: 'border-violet-400/25 bg-violet-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(139,92,246,0.55)]',
  line: 'from-transparent via-violet-400/70 to-transparent',
  rgb: '139, 92, 246',
}

const limeAccent: Accent = {
  text: 'text-lime-300',
  iconBox: 'border-lime-400/25 bg-lime-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(163,230,53,0.5)]',
  line: 'from-transparent via-lime-400/70 to-transparent',
  rgb: '163, 230, 53',
}

const amberAccent: Accent = {
  text: 'text-amber-300',
  iconBox: 'border-amber-400/25 bg-amber-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(251,191,36,0.5)]',
  line: 'from-transparent via-amber-400/70 to-transparent',
  rgb: '251, 191, 36',
}

const featureItems: Feature[] = [
  {
    icon: IconPulse,
    title: 'Metrics that mean something',
    body: 'Counters, gauges and histograms with explicit aggregation semantics. Never average an average without weighting.',
    index: '01',
    accent: cyanAccent,
    visual: 'pulse',
  },
  {
    icon: IconLogs,
    title: 'Logs, treated as data',
    body: 'Search, filter and paginate safely. Untrusted text is never executed and always rendered as inert data.',
    index: '02',
    accent: violetAccent,
    visual: 'logs',
  },
  {
    icon: IconChart,
    title: 'Dashboards with guardrails',
    body: 'Configurable widgets validated on both sides. No arbitrary SQL, scripts or JavaScript in your definitions.',
    index: '03',
    accent: limeAccent,
    visual: 'widgets',
  },
  {
    icon: IconShield,
    title: 'Tenant isolation by design',
    body: 'Scoped credentials, explicit authorization and tests that prove one tenant cannot read another tenant.',
    index: '04',
    accent: amberAccent,
    visual: 'perimeter',
  },
  {
    icon: IconBolt,
    title: 'Bounded by default',
    body: 'Payload limits, retries with backoff and predictable failure behavior. Backpressure you can reason about.',
    index: '05',
    accent: cyanAccent,
    visual: 'limits',
  },
  {
    icon: IconLayers,
    title: 'Open contracts, open standards',
    body: 'Explicit versioned APIs and OpenTelemetry-native collection. Integrate without vendor lock-in.',
    index: '06',
    accent: violetAccent,
    visual: 'stack',
  },
]

const logLines = [
  { t: '12:04:11', level: 'INFO', text: 'auth.service login ok', levelCls: 'text-cyan-300' },
  { t: '12:04:12', level: 'WARN', text: 'cache.miss key=session:8f3a', levelCls: 'text-amber-300' },
  { t: '12:04:13', level: 'ERROR', text: 'db.pool exhausted (256/256)', levelCls: 'text-rose-400' },
]

const limitBars = [
  { label: 'batch.size', pct: 80, value: '800 / 1000' },
  { label: 'payload.max', pct: 62, value: '1.0 / 1.6 MB' },
  { label: 'retry.backoff', pct: 35, value: '300ms base' },
]

function FeatureVisual({ visual }: { visual: VisualKind }) {
  switch (visual) {
    case 'pulse':
      return (
        <div className="rounded-xl border border-white/5 bg-[#05070d]/70 p-3">
          <div className="mono flex items-center justify-between gap-2 text-[9px] uppercase tracking-wider text-slate-500">
            <span>mem.utilization</span>
            <span>counter · gauge · histogram</span>
          </div>
          <div className="mt-2">
            <Sparkline
              values={[12, 18, 15, 26, 22, 38, 34, 52, 47, 66, 58, 78, 72, 90]}
              stroke="#22d3ee"
              gradientId="vp-pulse"
              height={38}
            />
          </div>
        </div>
      )
    case 'logs':
      return (
        <div className="rounded-xl border border-white/5 bg-[#05070d]/70 p-3">
          <div className="mono space-y-1.5 text-[10px] leading-relaxed">
            {logLines.map((line) => (
              <div key={line.t} className="flex gap-2">
                <span className="text-slate-600">{line.t}</span>
                <span className={`${line.levelCls} w-11 shrink-0`}>{line.level.padEnd(5, ' ')}</span>
                <span className="truncate text-slate-400">{line.text}</span>
              </div>
            ))}
          </div>
        </div>
      )
    case 'widgets':
      return (
        <div className="rounded-xl border border-white/5 bg-[#05070d]/70 p-3">
          <div className="grid grid-cols-2 gap-2">
            <div className="col-span-2 rounded-lg border border-white/5 bg-white/[0.02] p-2.5">
              <div className="mono text-[9px] uppercase tracking-wider text-slate-500">cpu.usage</div>
              <div className="font-display text-xl font-semibold text-lime-300">42.8%</div>
              <div className="mt-1">
                <Sparkline
                  values={[10, 14, 12, 20, 18, 28, 24, 36, 32, 44, 40, 52, 48, 60]}
                  stroke="#a3e635"
                  gradientId="vp-widgets"
                  height={26}
                />
              </div>
            </div>
            <div className="rounded-lg border border-white/5 bg-white/[0.02] p-2.5">
              <div className="mono text-[9px] uppercase tracking-wider text-slate-500">p99 latency</div>
              <div className="font-display mt-0.5 text-base font-semibold text-slate-100">182ms</div>
            </div>
            <div className="rounded-lg border border-white/5 bg-white/[0.02] p-2.5">
              <div className="mono text-[9px] uppercase tracking-wider text-slate-500">health</div>
              <div className="mt-1 flex items-center gap-1.5">
                <span className="glow-dot h-1.5 w-1.5 rounded-full bg-emerald-400 text-emerald-400" />
                <span className="font-display text-sm font-semibold text-emerald-300">UP</span>
              </div>
            </div>
          </div>
        </div>
      )
    case 'perimeter':
      return (
        <div className="rounded-xl border border-white/5 bg-[#05070d]/70 p-3">
          <div className="mono mb-2 flex justify-between text-[9px] uppercase tracking-wider text-slate-500">
            <span>scoped perimeter</span>
            <span>tenant boundary</span>
          </div>
          <div className="relative flex h-16 items-center justify-center">
            <div className="absolute inset-x-1 top-1 h-14 rounded-lg border border-amber-400/20" />
            <div className="absolute inset-x-3 top-2.5 h-11 rounded-md border border-amber-400/30" />
            <div className="relative flex h-8 w-28 items-center justify-center rounded border border-amber-400/45 bg-amber-400/5">
              <span className="mono text-[9px] uppercase tracking-[0.18em] text-amber-300">acme-prod</span>
              <span className="absolute -left-px -top-px h-2 w-2 border-l-2 border-t-2 border-amber-300" />
              <span className="absolute -right-px -top-px h-2 w-2 border-r-2 border-t-2 border-amber-300" />
              <span className="absolute -bottom-px -left-px h-2 w-2 border-b-2 border-l-2 border-amber-300" />
              <span className="absolute -bottom-px -right-px h-2 w-2 border-b-2 border-r-2 border-amber-300" />
            </div>
          </div>
        </div>
      )
    case 'limits':
      return (
        <div className="rounded-xl border border-white/5 bg-[#05070d]/70 p-3">
          <div className="mono mb-2 flex justify-between text-[9px] uppercase tracking-wider text-slate-500">
            <span>enforced quotas</span>
            <span className="text-rose-400/80">limit</span>
          </div>
          <div className="space-y-2.5">
            {limitBars.map((bar) => (
              <div key={bar.label}>
                <div className="mono flex justify-between text-[9px] uppercase tracking-wider text-slate-500">
                  <span>{bar.label}</span>
                  <span className="text-slate-400">{bar.value}</span>
                </div>
                <div className="relative mt-1 h-1.5 overflow-hidden rounded-full bg-white/5">
                  <div className="h-full rounded-full bg-cyan-400/80" style={{ width: `${bar.pct}%` }} />
                  <div className="absolute right-[10%] top-0 h-full w-px bg-rose-400/70" />
                </div>
              </div>
            ))}
          </div>
        </div>
      )
    case 'stack':
      return (
        <div className="rounded-xl border border-white/5 bg-[#05070d]/70 p-3">
          <div className="mono mb-2 flex justify-between text-[9px] uppercase tracking-wider text-slate-500">
            <span>versioned contracts</span>
            <span>v1 · v2</span>
          </div>
          <div className="relative h-14">
            <div className="absolute inset-x-1.5 top-0 h-12 rounded-lg border border-violet-400/20 bg-[#0b101b]" />
            <div className="absolute inset-x-0.5 top-2 h-12 rounded-lg border border-violet-400/35 bg-[#0d1322]" />
            <div className="absolute inset-x-0 top-3.5 flex h-12 flex-col justify-center gap-0.5 rounded-lg border border-violet-400/60 bg-[#111a2e] px-2.5 shadow-lg shadow-violet-500/10">
              <span className="mono text-[9px] text-violet-300">POST /v1/telemetry</span>
              <span className="mono text-[8px] text-slate-500">openapi · otlp · grpc</span>
            </div>
          </div>
        </div>
      )
  }
}

type FeatureCardProps = {
  feature: Feature
  inView: boolean
  delay: number
}

function FeatureCard({ feature, inView, delay }: FeatureCardProps) {
  const { icon: Icon, title, body, index, accent, visual } = feature

  function handleMouseMove(event: React.MouseEvent<HTMLDivElement>) {
    const node = event.currentTarget
    const rect = node.getBoundingClientRect()
    node.style.setProperty('--mx', `${event.clientX - rect.left}px`)
    node.style.setProperty('--my', `${event.clientY - rect.top}px`)
  }

  return (
    <div
      onMouseMove={handleMouseMove}
      className={`card-hover group relative flex flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#0b101b]/70 p-6 ${
        inView ? 'vp-card' : 'opacity-0'
      }`}
      style={{ animationDelay: `${delay}ms` }}
    >
      <div
        className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-500 group-hover:opacity-100"
        style={{
          background: `radial-gradient(340px circle at var(--mx, 50%) var(--my, 50%), rgba(${accent.rgb}, 0.08), transparent 70%)`,
        }}
      />

      <div className="relative flex items-start justify-between">
        <div
          className={`flex h-10 w-10 items-center justify-center rounded-xl border ${accent.iconBox} ${accent.iconGlow} transition-shadow duration-500`}
        >
          <Icon className={`h-5 w-5 ${accent.text} transition-transform duration-300 group-hover:scale-110`} />
        </div>
        <span className="mono text-xs text-slate-600 transition-colors duration-300 group-hover:text-slate-400">
          {index}
        </span>
      </div>

      <h3 className="font-display relative mt-5 text-lg font-semibold text-slate-100 transition-colors duration-300 group-hover:text-white">
        {title}
      </h3>
      <p className="relative mt-2 flex-1 text-sm leading-relaxed text-slate-400">{body}</p>

      <div className="relative mt-5">
        <FeatureVisual visual={visual} />
      </div>

      <div
        className={`absolute inset-x-6 bottom-0 h-px bg-gradient-to-r opacity-0 transition-opacity duration-500 group-hover:opacity-100 ${accent.line}`}
      />
    </div>
  )
}

export function ValueProposition() {
  const { ref, inView } = useInView<HTMLDivElement>()

  return (
    <section id="features" className="relative overflow-hidden border-y border-white/5 bg-[#070a12] py-24">
      <style>{`
        @keyframes vp-fade-up {
          from { opacity: 0; transform: translateY(16px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .vp-card {
          opacity: 0;
          animation: vp-fade-up 0.7s cubic-bezier(0.22, 1, 0.36, 1) forwards;
        }
        @media (prefers-reduced-motion: reduce) {
          .vp-card { opacity: 1; animation: none; }
        }
      `}</style>

      <div className="bg-grid-fine absolute inset-0 opacity-60" />
      <div className="orb -left-24 top-16 h-72 w-72 bg-cyan-500/35" />
      <div className="orb -right-24 bottom-4 h-80 w-80 bg-violet-600/30" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-cyan-400/40 to-transparent" />

      <div className="relative mx-auto max-w-7xl px-6">
        <div className="flex flex-wrap items-end justify-between gap-6">
          <SectionHeading
            tag="Capabilities"
            title={
              <>
                Everything you need to <span className="text-gradient">stop guessing.</span>
              </>
            }
            body="Six principles, one platform. Each capability is built on explicit contracts and validated boundaries."
          />
    
        </div>

        <div ref={ref} className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {featureItems.map((feature, index) => (
            <FeatureCard key={feature.index} feature={feature} inView={inView} delay={index * 90} />
          ))}
        </div>
      </div>
    </section>
  )
}
