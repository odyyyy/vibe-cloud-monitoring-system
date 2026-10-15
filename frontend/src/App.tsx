import { useEffect, useRef, useState } from 'react'

type IconProps = { className?: string }

const Logo = ({ className = 'h-7 w-7' }: IconProps) => (
  <svg viewBox="0 0 32 32" fill="none" className={className} aria-hidden="true">
    <rect x="1" y="1" width="30" height="30" rx="8" stroke="url(#lg)" strokeWidth="1.6" />
    <path d="M7 20.5c3.2 0 4.2-8 7.6-8s3 6 5.2 6 1.9-2.4 4.2-2.4" stroke="#67e8f9" strokeWidth="2" strokeLinecap="round" />
    <circle cx="24.5" cy="22.5" r="2.2" fill="#a78bfa" />
    <defs>
      <linearGradient id="lg" x1="0" y1="0" x2="32" y2="32">
        <stop stopColor="#22d3ee" />
        <stop offset="1" stopColor="#8b5cf6" />
      </linearGradient>
    </defs>
  </svg>
)

const IconArrow = ({ className = 'h-4 w-4' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M5 12h14M13 6l6 6-6 6" />
  </svg>
)

const IconCopy = ({ className = 'h-4 w-4' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <rect x="9" y="9" width="11" height="11" rx="2" />
    <path d="M5 15V6a2 2 0 0 1 2-2h9" />
  </svg>
)

const IconCheck = ({ className = 'h-4 w-4' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M20 6 9 17l-5-5" />
  </svg>
)

const IconTerminal = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="m4 17 6-6-6-6M12 19h8" />
  </svg>
)

const IconPulse = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M2 12h4l3-8 4 16 3-10 2 2h4" />
  </svg>
)

const IconLogs = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01" />
  </svg>
)

const IconChart = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M3 3v18h18" />
    <path d="M7 14l3-4 3 3 4-6" />
  </svg>
)

const IconShield = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
    <path d="m9 12 2 2 4-4" />
  </svg>
)

const IconBolt = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
  </svg>
)

const IconLayers = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="m12 2 9 5-9 5-9-5 9-5Z" />
    <path d="m3 12 9 5 9-5M3 17l9 5 9-5" />
  </svg>
)

const IconGlobe = ({ className = 'h-5 w-5' }: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <circle cx="12" cy="12" r="9" />
    <path d="M3 12h18M12 3a15 15 0 0 1 0 18 15 15 0 0 1 0-18Z" />
  </svg>
)

function useCountUp(target: number, duration = 1600, decimals = 0): number {
  const [value, setValue] = useState(0)
  const startRef = useRef<number | null>(null)

  useEffect(() => {
    let raf = 0
    const step = (now: number) => {
      if (startRef.current === null) startRef.current = now
      const progress = Math.min((now - startRef.current) / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      setValue(parseFloat((target * eased).toFixed(decimals)))
      if (progress < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [target, duration, decimals])

  return value
}

const MARQUEE = [
  'Metrics',
  'Logs',
  'Traces',
  'Dashboards',
  'Service health',
  'Alerts',
  'Tenant isolation',
  'OpenTelemetry',
  'Agent enrollment',
  'Time-series',
]

type SparklineProps = { values: number[]; stroke: string; gradientId: string; height?: number }

function Sparkline({ values, stroke, gradientId, height = 40 }: SparklineProps) {
  const width = 160
  const pad = 3
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const stepX = (width - pad * 2) / (values.length - 1)

  const coords = values.map((v, i) => {
    const x = pad + i * stepX
    const y = height - pad - ((v - min) / range) * (height - pad * 2)
    return { x: Number(x.toFixed(2)), y: Number(y.toFixed(2)) }
  })

  const points = coords.map((c) => `${c.x},${c.y}`).join(' ')
  const area = `M ${pad},${height} L ${points.replace(/ /g, ' L ')} L ${width - pad},${height} Z`

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="h-10 w-full" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={stroke} stopOpacity="0.35" />
          <stop offset="100%" stopColor={stroke} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <polyline
        className="spark-path"
        points={points}
        fill="none"
        stroke={stroke}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={coords[coords.length - 1].x} cy={coords[coords.length - 1].y} r="3" fill={stroke} />
    </svg>
  )
}

const METRICS = [
  { label: 'CPU utilization', value: '42.8', unit: '%', color: '#22d3ee', gradient: 'g-cpu', series: [18, 24, 21, 30, 27, 38, 34, 44, 41, 52, 47, 42] },
  { label: 'Latency p99', value: '182', unit: 'ms', color: '#8b5cf6', gradient: 'g-lat', series: [90, 120, 104, 148, 132, 176, 158, 210, 186, 240, 198, 182] },
  { label: 'Throughput', value: '1.4k', unit: 'req/s', color: '#a3e635', gradient: 'g-thr', series: [620, 780, 700, 940, 880, 1120, 1040, 1380, 1260, 1520, 1420, 1400] },
]

const LOG_LINES = [
  { t: '14:02:11', level: 'INFO', service: 'payments', msg: 'charge captured 42.00 USD in 88ms' },
  { t: '14:02:09', level: 'WARN', service: 'edge-proxy', msg: 'upstream latency above p99 threshold' },
  { t: '14:02:04', level: 'ERROR', service: 'auth', msg: 'token refresh failed, retrying (2/3)' },
  { t: '14:01:58', level: 'INFO', service: 'web', msg: 'GET /checkout 200 in 12ms' },
  { t: '14:01:47', level: 'INFO', service: 'worker', msg: 'batch job reconcile completed: 1,024 rows' },
  { t: '14:01:31', level: 'DEBUG', service: 'cache', msg: 'hit ratio 0.94 across 3 shards' },
]

const FEATURES = [
  {
    icon: IconPulse,
    title: 'Metrics that mean something',
    body: 'Counters, gauges and histograms with explicit aggregation semantics. Never average an average without weighting.',
    accent: 'text-cyan-300',
  },
  {
    icon: IconLogs,
    title: 'Logs, treated as data',
    body: 'Search, filter and paginate safely. Untrusted text is never executed and always rendered as inert data.',
    accent: 'text-violet-300',
  },
  {
    icon: IconChart,
    title: 'Dashboards with guardrails',
    body: 'Configurable widgets validated on both sides. No arbitrary SQL, scripts or JavaScript in your definitions.',
    accent: 'text-lime-300',
  },
  {
    icon: IconShield,
    title: 'Tenant isolation by design',
    body: 'Scoped credentials, explicit authorization and tests that prove one tenant cannot read another tenant.',
    accent: 'text-amber-300',
  },
  {
    icon: IconBolt,
    title: 'Bounded by default',
    body: 'Payload limits, retries with backoff and predictable failure behavior. Backpressure you can reason about.',
    accent: 'text-cyan-300',
  },
  {
    icon: IconLayers,
    title: 'Open contracts, open standards',
    body: 'Explicit versioned APIs and OpenTelemetry-native collection. Integrate without vendor lock-in.',
    accent: 'text-violet-300',
  },
]

const STEPS = [
  { n: '01', title: 'Enroll', body: 'Create a project and environment, then issue a scoped agent credential.', icon: IconLayers },
  { n: '02', title: 'Deploy', body: 'Run the Go collector as a Docker container in your own infrastructure.', icon: IconGlobe },
  { n: '03', title: 'Ingest', body: 'Metrics and logs stream over authenticated HTTPS with bounded batches.', icon: IconPulse },
  { n: '04', title: 'Explore', body: 'Query, visualize and share dashboards from a single pane of glass.', icon: IconChart },
]

const STATS = [
  { value: 99.95, suffix: '%', decimals: 2, label: 'Ingestion uptime' },
  { value: 1.2, suffix: 'M', decimals: 1, label: 'Series per minute' },
  { value: 248, suffix: 'ms', decimals: 0, label: 'Query p95' },
  { value: 34, suffix: 'ms', decimals: 0, label: 'Agent batch size' },
]

const TABS = [
  {
    id: 'docker',
    label: 'Docker',
    code: `# one container, scoped to your tenant
docker run -d \\
  --name cloudmonitor-agent \\
  -e CM_AGENT_TOKEN=cmrt_live_9f3...a2 \\
  -e CM_PROJECT=acme-prod \\
  -e CM_ENV=production \\
  ghcr.io/cloudmonitor/agent:latest`,
  },
  {
    id: 'config',
    label: 'Config',
    code: `# agent.yaml
server: https://ingest.cloudmonitor.io
project: acme-prod
environment: production
host: prod-worker-01

metrics:
  interval: 15s
  batch: 200
  buffers: 2048

logs:
  - path: /var/log/app/*.log
    service: payments`,
  },
]

function SectionHeading({ tag, title, body }: { tag: string; title: React.ReactNode; body?: string }) {
  return (
    <div className="max-w-2xl">
      <span className="section-tag mono">{tag}</span>
      <h2 className="font-display mt-4 text-3xl font-semibold leading-tight tracking-tight text-slate-100 sm:text-4xl">
        {title}
      </h2>
      {body ? <p className="mt-4 text-base leading-relaxed text-slate-400">{body}</p> : null}
    </div>
  )
}

function Navbar() {
  return (
    <header className="fixed inset-x-0 top-0 z-50 border-b border-white/5 bg-[#05070d]/70 backdrop-blur-xl">
      <nav className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
        <a href="#top" className="flex items-center gap-2.5">
          <Logo />
          <span className="font-display text-lg font-semibold tracking-tight text-slate-100">
            Cloud<span className="text-cyan-300">Monitor</span>
          </span>
        </a>

        <div className="hidden items-center gap-8 text-sm text-slate-400 md:flex">
          <a href="#features" className="transition-colors hover:text-slate-100">
            Features
          </a>
          <a href="#flow" className="transition-colors hover:text-slate-100">
            How it works
          </a>
          <a href="#install" className="transition-colors hover:text-slate-100">
            Install
          </a>
          <a href="#pricing" className="transition-colors hover:text-slate-100">
            Pricing
          </a>
        </div>

        <div className="flex items-center gap-3">
          <a href="#cta" className="hidden text-sm text-slate-400 transition-colors hover:text-slate-100 sm:block">
            Sign in
          </a>
          <a
            href="#cta"
            className="group inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-cyan-400 to-violet-500 px-4 py-2 text-sm font-semibold text-[#05070d] transition-transform hover:scale-[1.03]"
          >
            Start free
            <IconArrow className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
          </a>
        </div>
      </nav>
    </header>
  )
}

function LivePanel() {
  const cpu = useCountUp(42.8, 1600, 1)
  const lat = useCountUp(182, 1600, 0)
  const thr = useCountUp(1400, 1600, 0)

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
          {[
            { label: METRICS[0].label, value: `${cpu.toFixed(1)}%`, color: METRICS[0].color, g: METRICS[0].gradient, s: METRICS[0].series },
            { label: METRICS[1].label, value: `${Math.round(lat)}ms`, color: METRICS[1].color, g: METRICS[1].gradient, s: METRICS[1].series },
            { label: METRICS[2].label, value: `${Math.round(thr)}`, color: METRICS[2].color, g: METRICS[2].gradient, s: METRICS[2].series },
          ].map((m) => (
            <div key={m.label} className="rounded-xl border border-white/5 bg-white/[0.02] p-3">
              <div className="mono truncate text-[10px] uppercase tracking-wider text-slate-500">{m.label}</div>
              <div className="font-display mt-1 text-xl font-semibold text-slate-100">{m.value}</div>
              <div className="mt-2">
                <Sparkline values={m.s} stroke={m.color} gradientId={m.g} />
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
              {[...LOG_LINES, ...LOG_LINES].map((line, i) => (
                <div key={i} className="mono flex gap-2 text-[10.5px] leading-relaxed">
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

function Hero() {
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
            <a
              href="#cta"
              className="group inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-cyan-400 to-violet-500 px-6 py-3 text-sm font-semibold text-[#05070d] transition-transform hover:scale-[1.03]"
            >
              Start observing free
              <IconArrow className="h-4 w-4 transition-transform group-hover:translate-x-1" />
            </a>
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

function Marquee() {
  return (
    <section className="relative border-y border-white/5 bg-[#080b14]/60 py-5">
      <div className="pointer-events-none absolute inset-y-0 left-0 z-10 w-24 bg-gradient-to-r from-[#05070d] to-transparent" />
      <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-[#05070d] to-transparent" />
      <div className="overflow-hidden">
        <div className="marquee">
          {[...MARQUEE, ...MARQUEE].map((item, i) => (
            <span key={i} className="mono flex items-center gap-6 px-6 text-sm uppercase tracking-[0.25em] text-slate-500">
              <span className="text-cyan-400">/</span>
              {item}
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}

function Features() {
  return (
    <section id="features" className="relative py-24">
      <div className="mx-auto max-w-7xl px-6">
        <div className="flex flex-col items-start justify-between gap-8 md:flex-row md:items-end">
          <SectionHeading
            tag="Capabilities"
            title={
              <>
                Signal, not <span className="text-gradient">noise.</span>
              </>
            }
            body="Every capability is built to be reliable first — correct semantics, explicit limits and predictable failure behavior."
          />
          <p className="mono max-w-xs text-xs leading-relaxed text-slate-500">
            {'//'} reliability before feature count.
            <br />
            {'//'} security and tenant isolation by design.
          </p>
        </div>

        <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <article
              key={f.title}
              className="card-hover group relative overflow-hidden rounded-2xl border border-white/10 bg-[#0b101b]/60 p-6"
            >
              <div className="absolute -right-10 -top-10 h-32 w-32 rounded-full bg-cyan-500/5 blur-2xl transition-opacity group-hover:opacity-100" />
              <div className={`inline-flex rounded-lg border border-white/10 bg-white/5 p-2.5 ${f.accent}`}>
                <f.icon />
              </div>
              <h3 className="font-display mt-5 text-lg font-semibold text-slate-100">{f.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">{f.body}</p>
              <div className="mono mt-5 flex items-center gap-2 text-xs text-slate-600">
                <span className="h-px w-6 bg-gradient-to-r from-cyan-400 to-transparent" />
                bounded · validated · scoped
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}

function Flow() {
  return (
    <section id="flow" className="relative border-y border-white/5 bg-[#070a12] py-24">
      <div className="bg-grid-fine absolute inset-0 opacity-60" />
      <div className="relative mx-auto max-w-7xl px-6">
        <SectionHeading
          tag="Data flow"
          title={
            <>
              From agent to dashboard in <span className="text-gradient">one flow.</span>
            </>
          }
          body="The control plane stays separate from the telemetry plane — but for your team, the path is a straight line."
        />

        <div className="mt-14 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((step, i) => (
            <div key={step.n} className="relative">
              <div className="card-hover h-full rounded-2xl border border-white/10 bg-[#0b101b]/70 p-6">
                <div className="flex items-center justify-between">
                  <span className="mono text-xs text-cyan-400">{step.n}</span>
                  <step.icon className="h-5 w-5 text-slate-500" />
                </div>
                <h3 className="font-display mt-6 text-lg font-semibold text-slate-100">{step.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-slate-400">{step.body}</p>
              </div>
              {i < STEPS.length - 1 ? (
                <div className="absolute -right-4 top-1/2 hidden h-px w-4 -translate-y-1/2 bg-gradient-to-r from-cyan-400/60 to-transparent lg:block" />
              ) : null}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function Install() {
  const [active, setActive] = useState(TABS[0].id)
  const [copied, setCopied] = useState(false)

  const current = TABS.find((t) => t.id === active) ?? TABS[0]

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(current.code)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
    } catch {
      setCopied(false)
    }
  }

  return (
    <section id="install" className="relative py-24">
      <div className="orb left-1/2 top-1/3 h-[420px] w-[620px] -translate-x-1/2 bg-violet-600/15" />
      <div className="relative mx-auto max-w-7xl px-6">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <div>
            <SectionHeading
              tag="Quick start"
              title={
                <>
                  Observe your first service in <span className="text-gradient">five minutes.</span>
                </>
              }
              body="One scoped credential, one container, and telemetry starts flowing. Credentials rotate without rebuilding the image."
            />

            <ol className="mt-8 space-y-4">
              {[
                'Create a project and environment in the console.',
                'Copy the scoped enrollment token — it only grants telemetry write access.',
                'Run the agent and watch metrics and logs appear in real time.',
              ].map((text, i) => (
                <li key={text} className="flex gap-3 text-sm text-slate-300">
                  <span className="mono flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-cyan-400/40 text-[11px] text-cyan-300">
                    {i + 1}
                  </span>
                  <span className="leading-relaxed">{text}</span>
                </li>
              ))}
            </ol>

            <div className="mt-8 flex flex-wrap gap-2">
              <span className="chip mono">Go 1.22+</span>
              <span className="chip mono">Docker</span>
              <span className="chip mono">OpenTelemetry</span>
              <span className="chip mono">HTTPS only</span>
            </div>
          </div>

          <div className="gradient-border shimmer rounded-2xl">
            <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
              <div className="flex gap-1">
                {TABS.map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => setActive(t.id)}
                    className={`mono rounded-md px-3 py-1.5 text-xs transition-colors ${
                      active === t.id ? 'bg-white/10 text-slate-100' : 'text-slate-500 hover:text-slate-300'
                    }`}
                  >
                    {t.label}
                  </button>
                ))}
              </div>
              <button
                type="button"
                onClick={copy}
                className="inline-flex items-center gap-1.5 rounded-md border border-white/10 px-2.5 py-1.5 text-xs text-slate-400 transition-colors hover:border-cyan-400/40 hover:text-cyan-300"
              >
                {copied ? <IconCheck className="h-3.5 w-3.5 text-lime-300" /> : <IconCopy className="h-3.5 w-3.5" />}
                {copied ? 'Copied' : 'Copy'}
              </button>
            </div>
            <pre className="mono overflow-x-auto p-5 text-[12.5px] leading-relaxed text-slate-300">
              <code>{current.code}</code>
            </pre>
          </div>
        </div>
      </div>
    </section>
  )
}

function Stats() {
  return (
    <section className="relative border-y border-white/5 bg-[#070a12] py-16">
      <div className="mx-auto grid max-w-7xl gap-10 px-6 sm:grid-cols-2 lg:grid-cols-4">
        {STATS.map((s) => (
          <Stat key={s.label} {...s} />
        ))}
      </div>
    </section>
  )
}

function Stat({ value, suffix, decimals, label }: { value: number; suffix: string; decimals: number; label: string }) {
  const display = useCountUp(value, 1800, decimals)
  return (
    <div className="text-center">
      <div className="font-display text-4xl font-bold tracking-tight text-slate-100 sm:text-5xl">
        {display.toFixed(decimals)}
        <span className="text-gradient">{suffix}</span>
      </div>
      <div className="mono mt-2 text-xs uppercase tracking-[0.2em] text-slate-500">{label}</div>
    </div>
  )
}

function Cta() {
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)

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

        {done ? (
          <div className="mx-auto mt-8 flex max-w-md items-center justify-center gap-2 rounded-full border border-lime-400/30 bg-lime-400/10 px-6 py-3 text-sm text-lime-200">
            <IconCheck className="h-4 w-4" />
            You are on the list — check your inbox.
          </div>
        ) : (
          <form
            className="mx-auto mt-8 flex max-w-md flex-col gap-3 sm:flex-row"
            onSubmit={(e) => {
              e.preventDefault()
              if (email.trim()) setDone(true)
            }}
          >
            <label htmlFor="cta-email" className="sr-only">
              Work email
            </label>
            <input
              id="cta-email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@company.com"
              className="mono flex-1 rounded-full border border-white/10 bg-[#0b101b]/80 px-5 py-3 text-sm text-slate-100 outline-none transition-colors placeholder:text-slate-600 focus:border-cyan-400/50"
            />
            <button
              type="submit"
              className="group inline-flex items-center justify-center gap-2 rounded-full bg-gradient-to-r from-cyan-400 to-violet-500 px-6 py-3 text-sm font-semibold text-[#05070d] transition-transform hover:scale-[1.03]"
            >
              Create project
              <IconArrow className="h-4 w-4 transition-transform group-hover:translate-x-1" />
            </button>
          </form>
        )}

        <p className="mono mt-5 text-xs text-slate-600">
          14-day trial · tenant isolation included · cancel anytime
        </p>
      </div>
    </section>
  )
}

function Footer() {
  const columns = [
    { title: 'Product', links: ['Metrics', 'Logs', 'Dashboards', 'Agents', 'Alerts'] },
    { title: 'Developers', links: ['Documentation', 'API reference', 'OpenTelemetry', 'Status'] },
    { title: 'Company', links: ['About', 'Security', 'Careers', 'Contact'] },
  ]

  return (
    <footer className="relative border-t border-white/5 bg-[#05070d] py-14">
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-10 md:grid-cols-[1.4fr_repeat(3,1fr)]">
          <div>
            <div className="flex items-center gap-2.5">
              <Logo />
              <span className="font-display text-lg font-semibold text-slate-100">
                Cloud<span className="text-cyan-300">Monitor</span>
              </span>
            </div>
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-slate-500">
              A dependable cloud observability platform. Reliability and tenant isolation first, polish second.
            </p>
          </div>

          {columns.map((col) => (
            <div key={col.title}>
              <h4 className="mono text-xs uppercase tracking-[0.2em] text-slate-500">{col.title}</h4>
              <ul className="mt-4 space-y-2.5">
                {col.links.map((link) => (
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
          <p className="mono text-xs text-slate-600">© 2026 CloudMonitor. All signals reserved.</p>
          <div className="mono flex items-center gap-2 text-xs text-slate-600">
            <span className="glow-dot h-1.5 w-1.5 rounded-full bg-emerald-400 text-emerald-400" />
            all systems operational
          </div>
        </div>
      </div>
    </footer>
  )
}

export default function App() {
  return (
    <div className="noise min-h-screen bg-[#05070d]">
      <Navbar />
      <main>
        <Hero />
        <Marquee />
        <Features />
        <Flow />
        <Install />
        <Stats />
        <Cta />
      </main>
      <Footer />
    </div>
  )
}
