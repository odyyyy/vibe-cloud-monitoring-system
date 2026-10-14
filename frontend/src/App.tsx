import React, { useEffect } from 'react'

type IconProps = { className?: string }

const stroke = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  viewBox: '0 0 24 24',
}

function IconLayers({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <path d="M12 3 3 7.5 12 12l9-4.5L12 3Z" />
      <path d="M3 12.5 12 17l9-4.5" />
      <path d="M3 17 12 21.5 21 17" />
    </svg>
  )
}

function IconTerminal({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="m7 9 3 3-3 3" />
      <path d="M13 15h4" />
    </svg>
  )
}

function IconActivity({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <path d="M3 12h4l2.5-7 4 14L16 12h5" />
    </svg>
  )
}

function IconDashboard({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <rect x="3" y="3" width="8" height="10" rx="1.5" />
      <rect x="13" y="3" width="8" height="6" rx="1.5" />
      <rect x="13" y="11" width="8" height="10" rx="1.5" />
      <rect x="3" y="15" width="8" height="6" rx="1.5" />
    </svg>
  )
}

function IconPulse({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <path d="M3 12h3l2-4 3 8 2.5-5 1.5 3h6" />
      <circle cx="19.5" cy="14" r="1.6" />
    </svg>
  )
}

function IconClock({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </svg>
  )
}

function IconShield({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <path d="M12 3 5 6v5.5c0 4.3 2.9 7.9 7 9.5 4.1-1.6 7-5.2 7-9.5V6l-7-3Z" />
      <path d="m9 12 2 2 4-4.5" />
    </svg>
  )
}

function IconBlocks({ className = 'w-6 h-6' }: IconProps) {
  return (
    <svg {...stroke} className={className}>
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.5" />
      <path d="M17 14v6M14 17h6" />
    </svg>
  )
}

function Logo({ size = 'md' }: { size?: 'md' | 'sm' }) {
  const box = size === 'md' ? 'w-9 h-9' : 'w-8 h-8'
  const svg = size === 'md' ? 'w-5 h-5' : 'w-4 h-4'
  return (
    <span
      className={`${box} grid place-items-center rounded-md bg-[#7cff6b] text-black shadow-[0_0_22px_rgba(124,255,107,0.45)]`}
    >
      <svg className={svg} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 13h4l2-6 3 12 2.5-8 1.5 4h5" />
      </svg>
    </span>
  )
}

function useScrollReveal() {
  useEffect(() => {
    const nodes = Array.from(document.querySelectorAll<HTMLElement>('[data-reveal]'))
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduce || typeof IntersectionObserver === 'undefined') {
      nodes.forEach((n) => n.classList.add('is-visible'))
      return
    }
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            observer.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' }
    )
    nodes.forEach((n) => observer.observe(n))
    return () => observer.disconnect()
  }, [])
}

const features = [
  {
    id: '01',
    icon: IconLayers,
    title: 'Service & Environment Management',
    description:
      'Register and organize monitored services across projects and environments with explicit ownership boundaries.',
    tag: 'registry',
  },
  {
    id: '02',
    icon: IconTerminal,
    title: 'Log Ingestion & Search',
    description:
      'Collect, store, filter and search application logs with time-range, severity and structured attribute filters.',
    tag: 'logs',
  },
  {
    id: '03',
    icon: IconActivity,
    title: 'Metrics & Visualization',
    description:
      'Counters, gauges and histograms with correct aggregation semantics — no averages of averages, no silent unit changes.',
    tag: 'metrics',
  },
  {
    id: '04',
    icon: IconDashboard,
    title: 'Interactive Dashboards',
    description:
      'Configurable widgets: time-series charts, single-value stats, log tables and service status lists with validated configs.',
    tag: 'dashboards',
  },
  {
    id: '05',
    icon: IconPulse,
    title: 'Health Monitoring',
    description:
      'Synthetic HTTP/TCP checks with timeouts, TLS verification and execution history. UP / DEGRADED / DOWN / UNKNOWN states.',
    tag: 'health',
  },
  {
    id: '06',
    icon: IconClock,
    title: 'Time-Range Exploration',
    description:
      'One consistent time-range control across every view, with historical exploration and predictable refresh behavior.',
    tag: 'exploration',
  },
  {
    id: '07',
    icon: IconShield,
    title: 'Multi-Tenant Security',
    description:
      'Tenant isolation by design — every query, dashboard and ingestion path scoped to authorized organization boundaries.',
    tag: 'security',
  },
  {
    id: '08',
    icon: IconBlocks,
    title: 'Production-Ready Architecture',
    description:
      'Modular monolith, explicit API contracts, structured logging, Micrometer metrics and bounded resource consumption.',
    tag: 'architecture',
  },
]

const tickerLines = [
  { level: 'INFO', svc: 'ingest-api', msg: 'batch accepted size=512 tenant=acme duration=41ms' },
  { level: 'WARN', svc: 'query-svc', msg: 'scan rows=184220 above soft limit 150000' },
  { level: 'ERROR', svc: 'payments', msg: 'upstream timeout after 2000ms attempt=2/3' },
  { level: 'INFO', svc: 'health-prober', msg: 'check target=https://api.acme.io result=UP rtt=87ms' },
  { level: 'DEBUG', svc: 'dashboard-api', msg: 'widget config validated type=timeseries' },
  { level: 'INFO', svc: 'auth', msg: 'session issued subject=usr_19f scope=project:read' },
  { level: 'WARN', svc: 'metric-agg', msg: 'cardinality guard series=2048 limit=2048' },
  { level: 'INFO', svc: 'flyway', msg: 'migration V14 applied baseline=true' },
]

const logStream = [
  { t: '12:04:01', level: 'INFO', text: 'ingest-api  batch accepted size=512 duration=41ms' },
  { t: '12:04:03', level: 'WARN', text: 'query-svc   scan rows=184220 soft_limit=150000' },
  { t: '12:04:04', level: 'ERROR', text: 'payments    upstream timeout after 2000ms' },
  { t: '12:04:07', level: 'INFO', text: 'health      prober result=UP target=api.acme.io' },
  { t: '12:04:09', level: 'DEBUG', text: 'dashboard   widget validated type=timeseries' },
]

const services = [
  { name: 'ingest-api', state: 'UP' as const, uptime: '99.98%' },
  { name: 'query-svc', state: 'DEGRADED' as const, uptime: '99.42%' },
  { name: 'payments', state: 'DOWN' as const, uptime: '97.10%' },
]

const principles = [
  'Reliability before feature count',
  'Correctness before visual polish',
  'Security and tenant isolation by design',
  'Simple architecture before premature distribution',
  'Explicit API contracts',
  'Bounded resource consumption',
  'Reproducible local development',
  'Automated tests for critical behavior',
]

const stateStyles: Record<'UP' | 'DEGRADED' | 'DOWN', { dot: string; text: string }> = {
  UP: { dot: 'bg-[#7cff6b]', text: 'text-[#7cff6b]' },
  DEGRADED: { dot: 'bg-[#ffc466]', text: 'text-[#ffc466]' },
  DOWN: { dot: 'bg-[#ff5f7a]', text: 'text-[#ff5f7a]' },
}

const levelColor: Record<string, string> = {
  INFO: 'text-[#56d9ff]',
  WARN: 'text-[#ffc466]',
  ERROR: 'text-[#ff5f7a]',
  DEBUG: 'text-gray-500',
}

function SectionLabel({ index, text }: { index: string; text: string }) {
  return (
    <div className="flex items-center gap-3 font-mono text-xs tracking-[0.28em] uppercase text-gray-500">
      <span className="text-[#7cff6b]">{index}</span>
      <span className="h-px w-8 bg-white/15" />
      <span>{text}</span>
    </div>
  )
}

function App() {
  useScrollReveal()

  return (
    <div className="min-h-screen bg-[#06070b] text-gray-200 selection:bg-[#7cff6b]/30">
      {/* ================= HEADER ================= */}
      <header className="fixed top-0 inset-x-0 z-50 border-b border-white/10 bg-[#06070b]/80 backdrop-blur-xl">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between gap-6">
            <a href="#top" className="flex items-center gap-2.5">
              <Logo />
              <span className="font-mono text-sm font-semibold tracking-[0.2em] text-white">
                CLOUD<span className="text-[#7cff6b]">MONITOR</span>
              </span>
            </a>

            <nav className="hidden items-center gap-7 font-mono text-[13px] text-gray-400 md:flex">
              <a href="#features" className="transition-colors hover:text-[#7cff6b]">// features</a>
              <a href="#architecture" className="transition-colors hover:text-[#7cff6b]">// architecture</a>
              <a href="#principles" className="transition-colors hover:text-[#7cff6b]">// principles</a>
              <a href="#getting-started" className="transition-colors hover:text-[#7cff6b]">// quickstart</a>
            </nav>

            <div className="flex items-center gap-3">
              <span className="hidden items-center gap-2 font-mono text-[11px] uppercase tracking-widest text-gray-500 sm:flex">
                <span className="pulse-dot inline-block h-1.5 w-1.5 rounded-full bg-[#7cff6b]" />
                all systems nominal
              </span>
              <a
                href="#getting-started"
                className="rounded-md bg-[#7cff6b] px-4 py-2 font-mono text-[13px] font-semibold text-black transition-all hover:shadow-[0_0_26px_rgba(124,255,107,0.55)]"
              >
                deploy_local
              </a>
            </div>
          </div>
        </div>
      </header>

      {/* ================= HERO ================= */}
      <section id="top" className="relative overflow-hidden pt-32 pb-24 lg:pt-40 lg:pb-32">
        <div className="pointer-events-none absolute inset-0 bg-gridlines opacity-70" />
        <div className="pointer-events-none absolute -top-40 left-1/2 h-[520px] w-[820px] -translate-x-1/2 rounded-full bg-[#7cff6b]/10 blur-[130px]" />
        <div className="pointer-events-none absolute -left-32 top-1/3 h-[380px] w-[380px] rounded-full bg-[#56d9ff]/10 blur-[120px]" />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-[#06070b] to-transparent" />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="grid items-center gap-14 lg:grid-cols-[1.05fr_0.95fr]">
            {/* copy */}
            <div data-reveal>
              <div className="inline-flex items-center gap-2 rounded-full border border-[#7cff6b]/30 bg-[#7cff6b]/5 px-4 py-1.5 font-mono text-[11px] uppercase tracking-[0.3em] text-[#7cff6b]">
                <span className="pulse-dot inline-block h-1.5 w-1.5 rounded-full bg-[#7cff6b]" />
                observability platform · mvp
              </div>

              <h1 className="mt-7 font-mono text-4xl font-bold leading-[1.05] tracking-tight text-white sm:text-5xl lg:text-6xl">
                SEE EVERY SIGNAL.
                <br />
                <span className="text-glow text-[#7cff6b]">HIDE NO FAILURE.</span>
              </h1>

              <p className="mt-6 max-w-xl text-lg leading-relaxed text-gray-400">
                Collect, search, analyze and visualize logs and metrics from every service and
                environment — in one tenant-safe, locally reproducible observability platform
                inspired by the workflows of Datadog and Grafana.
              </p>

              <div className="mt-9 flex flex-col gap-3 sm:flex-row">
                <a
                  href="#getting-started"
                  className="group inline-flex items-center justify-center gap-2 rounded-md bg-[#7cff6b] px-7 py-3.5 font-mono text-sm font-semibold text-black transition-all hover:shadow-[0_0_36px_rgba(124,255,107,0.6)]"
                >
                  $ docker compose up -d
                  <span className="transition-transform group-hover:translate-x-1">→</span>
                </a>
                <a
                  href="#features"
                  className="inline-flex items-center justify-center gap-2 rounded-md border border-white/15 px-7 py-3.5 font-mono text-sm text-gray-300 transition-colors hover:border-[#56d9ff]/60 hover:text-[#56d9ff]"
                >
                  explore capabilities
                </a>
              </div>

              <dl className="mt-12 grid max-w-lg grid-cols-3 gap-6 border-t border-white/10 pt-6 font-mono">
                <div>
                  <dt className="text-[11px] uppercase tracking-widest text-gray-500">domains</dt>
                  <dd className="mt-1 text-2xl font-bold text-white">09</dd>
                </div>
                <div>
                  <dt className="text-[11px] uppercase tracking-widest text-gray-500">widget types</dt>
                  <dd className="mt-1 text-2xl font-bold text-white">04</dd>
                </div>
                <div>
                  <dt className="text-[11px] uppercase tracking-widest text-gray-500">external services</dt>
                  <dd className="mt-1 text-2xl font-bold text-[#7cff6b]">00</dd>
                </div>
              </dl>
            </div>

            {/* console */}
            <div data-reveal className="animate-float">
              <div className="panel relative rounded-xl shadow-[0_40px_120px_-50px_rgba(86,217,255,0.5)]">
                <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
                  <div className="flex items-center gap-2">
                    <span className="h-2.5 w-2.5 rounded-full bg-[#ff5f7a]/80" />
                    <span className="h-2.5 w-2.5 rounded-full bg-[#ffc466]/80" />
                    <span className="h-2.5 w-2.5 rounded-full bg-[#7cff6b]/80" />
                    <span className="ml-3 font-mono text-[11px] text-gray-500">
                      cloud-monitor — live tail
                    </span>
                  </div>
                  <span className="font-mono text-[11px] text-[#56d9ff]">● streaming</span>
                </div>

                {/* sparkline */}
                <div className="border-b border-white/10 px-4 py-4">
                  <div className="mb-2 flex items-baseline justify-between font-mono text-[11px] uppercase tracking-widest text-gray-500">
                    <span>req / min — last 15m</span>
                    <span className="text-[#7cff6b]">+12.4%</span>
                  </div>
                  <svg viewBox="0 0 320 72" className="h-20 w-full" role="img" aria-label="Requests per minute sparkline chart">
                    <defs>
                      <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#7cff6b" stopOpacity="0.35" />
                        <stop offset="100%" stopColor="#7cff6b" stopOpacity="0" />
                      </linearGradient>
                    </defs>
                    <path
                      d="M0 56 L24 50 L48 54 L72 38 L96 44 L120 30 L144 36 L168 22 L192 30 L216 18 L240 26 L264 12 L288 20 L320 8 L320 72 L0 72 Z"
                      fill="url(#sparkFill)"
                    />
                    <path
                      className="spark-path animate-hue"
                      d="M0 56 L24 50 L48 54 L72 38 L96 44 L120 30 L144 36 L168 22 L192 30 L216 18 L240 26 L264 12 L288 20 L320 8"
                      fill="none"
                      stroke="#7cff6b"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>

                {/* service states */}
                <div className="border-b border-white/10 px-4 py-3">
                  <div className="mb-2 font-mono text-[11px] uppercase tracking-widest text-gray-500">
                    service status
                  </div>
                  <ul className="space-y-2">
                    {services.map((s) => (
                      <li key={s.name} className="flex items-center justify-between font-mono text-[13px]">
                        <span className="flex items-center gap-2 text-gray-300">
                          <span className={`h-1.5 w-1.5 rounded-full ${stateStyles[s.state].dot}`} />
                          {s.name}
                        </span>
                        <span className={`text-[11px] ${stateStyles[s.state].text}`}>
                          {s.state} · {s.uptime}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>

                {/* log tail */}
                <div className="bg-scanlines px-4 py-3">
                  <div className="mb-2 font-mono text-[11px] uppercase tracking-widest text-gray-500">
                    log stream
                  </div>
                  <ul className="space-y-1.5 font-mono text-[11.5px] leading-relaxed">
                    {logStream.map((l) => (
                      <li key={l.t} className="truncate">
                        <span className="text-gray-600">{l.t}</span>{' '}
                        <span className={levelColor[l.level]}>{l.level.padEnd(5, ' ')}</span>{' '}
                        <span className="text-gray-400">{l.text}</span>
                      </li>
                    ))}
                    <li className="text-gray-500 caret">tail -f /var/log/platform</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ================= TICKER ================= */}
      <div className="relative border-y border-white/10 bg-white/[0.02] py-3">
        <div className="flex w-max animate-marquee gap-10 font-mono text-xs text-gray-500">
          {[...tickerLines, ...tickerLines, ...tickerLines, ...tickerLines].map((line, i) => (
            <span key={i} className="flex items-center gap-2 whitespace-nowrap">
              <span className={`px-1.5 py-0.5 text-[10px] ${levelColor[line.level]} border border-white/10 rounded`}>
                {line.level}
              </span>
              <span className="text-gray-400">{line.svc}</span>
              <span>{line.msg}</span>
              <span className="text-white/10">/</span>
            </span>
          ))}
        </div>
      </div>

      {/* ================= FEATURES ================= */}
      <section id="features" className="relative py-24 lg:py-32">
        <div className="pointer-events-none absolute right-0 top-40 h-[360px] w-[360px] rounded-full bg-[#56d9ff]/8 blur-[130px]" />
        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="mb-14 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between" data-reveal>
            <div className="max-w-2xl">
              <SectionLabel index="01" text="core capabilities" />
              <h2 className="mt-5 font-mono text-3xl font-bold tracking-tight text-white sm:text-4xl">
                Eight modules. <span className="text-[#56d9ff]">One boundary-respecting core.</span>
              </h2>
              <p className="mt-4 text-gray-400">
                Each capability is a logical module with clear ownership — not a microservice, not a
                monolithic blob.
              </p>
            </div>
            <div className="font-mono text-xs text-gray-500">
              <span className="text-[#7cff6b]">●</span> implemented incrementally, each stage ships
              working software
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {features.map((f, i) => {
              const Icon = f.icon
              const accent = i % 4 === 1 || i % 4 === 2 ? 'text-[#56d9ff]' : 'text-[#7cff6b]'
              return (
                <article
                  key={f.id}
                  data-reveal
                  className={`panel panel-hover group relative overflow-hidden rounded-xl p-6 ${
                    i === 0 || i === 5 ? 'lg:col-span-2' : ''
                  }`}
                >
                  <div className="pointer-events-none absolute -right-8 -top-8 h-24 w-24 rounded-full bg-white/5 blur-2xl transition-all group-hover:bg-[#7cff6b]/10" />
                  <div className="flex items-start justify-between">
                    <span className={`grid h-11 w-11 place-items-center rounded-lg border border-white/10 bg-white/5 ${accent}`}>
                      <Icon className="h-5 w-5" />
                    </span>
                    <span className="font-mono text-xs text-gray-600">{f.id}</span>
                  </div>
                  <h3 className="mt-5 font-mono text-[15px] font-semibold leading-snug text-white">
                    {f.title}
                  </h3>
                  <p className="mt-2.5 text-sm leading-relaxed text-gray-500">{f.description}</p>
                  <div className="mt-5 flex items-center justify-between border-t border-white/10 pt-3 font-mono text-[11px]">
                    <span className="text-gray-600">module/{f.tag}</span>
                    <span className="text-gray-700 transition-colors group-hover:text-[#7cff6b]">
                      read_more →
                    </span>
                  </div>
                </article>
              )
            })}
          </div>
        </div>
      </section>

      {/* ================= PIPELINE ================= */}
      <section className="border-y border-white/10 bg-white/[0.015] py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8" data-reveal>
          <div className="grid items-center gap-6 font-mono text-xs sm:grid-cols-2 lg:grid-cols-4">
            {[
              { step: '01', title: 'INGEST', desc: 'validated payloads, batch limits, scoped credentials' },
              { step: '02', title: 'STORE', desc: 'MySQL metadata + bounded telemetry retention' },
              { step: '03', title: 'QUERY', desc: 'server-side filtering, cursor pagination, capped results' },
              { step: '04', title: 'VISUALIZE', desc: 'typed widgets, honest charts, explicit empty states' },
            ].map((s, i) => (
              <div key={s.step} className="relative">
                <div className="flex items-center gap-3">
                  <span className="grid h-8 w-8 place-items-center rounded-full border border-[#7cff6b]/40 text-[#7cff6b]">
                    {s.step}
                  </span>
                  <span className="text-sm font-semibold tracking-[0.2em] text-white">{s.title}</span>
                  {i < 3 && <span className="ml-auto hidden text-gray-600 lg:inline">——→</span>}
                </div>
                <p className="mt-3 pl-11 leading-relaxed text-gray-500">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ================= ARCHITECTURE ================= */}
      <section id="architecture" className="relative py-24 lg:py-32">
        <div className="pointer-events-none absolute left-1/4 top-20 h-[340px] w-[340px] rounded-full bg-[#7cff6b]/8 blur-[130px]" />
        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="mb-14 max-w-2xl" data-reveal>
            <SectionLabel index="02" text="architecture" />
            <h2 className="mt-5 font-mono text-3xl font-bold tracking-tight text-white sm:text-4xl">
              A modular monolith, <span className="text-[#7cff6b]">documented honestly.</span>
            </h2>
            <p className="mt-4 text-gray-400">
              Proven technologies, explicit API contracts, no infrastructure you do not need yet.
            </p>
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            {[
              {
                title: 'backend',
                accent: 'text-[#7cff6b]',
                items: [
                  'Java 21 · Spring Boot 3.x',
                  'Spring Security + JWT',
                  'Spring Data JPA · MySQL 8',
                  'Flyway migrations',
                  'Micrometer + Actuator',
                  'OpenAPI contracts',
                ],
              },
              {
                title: 'frontend',
                accent: 'text-[#56d9ff]',
                items: [
                  'React 18 · TypeScript strict',
                  'Vite build system',
                  'TanStack Query — server state',
                  'Zustand — client state',
                  'Tailwind CSS',
                  'Chart.js time-series',
                ],
              },
              {
                title: 'infrastructure',
                accent: 'text-[#ffc466]',
                items: [
                  'Docker + Docker Compose',
                  'MySQL 8.x, local-first',
                  'No external paid services',
                  'Reproducible environments',
                  'Explicit migrations',
                  'Health via /actuator/health',
                ],
              },
            ].map((col, i) => (
              <div key={col.title} data-reveal className="panel panel-hover rounded-xl p-6">
                <div className="mb-5 flex items-center justify-between border-b border-white/10 pb-3">
                  <span className={`font-mono text-sm font-semibold tracking-[0.2em] ${col.accent}`}>
                    {col.title}
                  </span>
                  <span className="font-mono text-[11px] text-gray-600">0{i + 1}/03</span>
                </div>
                <ul className="space-y-3 font-mono text-[13px] text-gray-400">
                  {col.items.map((item) => (
                    <li key={item} className="flex gap-3">
                      <span className="text-gray-700">$</span>
                      <span className="transition-colors hover:text-white">{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ================= PRINCIPLES ================= */}
      <section id="principles" className="border-y border-white/10 bg-[#0a0b11] py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8" data-reveal>
          <SectionLabel index="03" text="product principles" />
          <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {principles.map((p, i) => (
              <div
                key={p}
                className="flex items-start gap-3 rounded-lg border border-white/10 bg-white/[0.02] px-4 py-4"
              >
                <span className="font-mono text-xs text-[#7cff6b]">{String(i + 1).padStart(2, '0')}</span>
                <span className="text-sm leading-snug text-gray-300">{p}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ================= GETTING STARTED ================= */}
      <section id="getting-started" className="py-24 lg:py-32">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="grid items-start gap-12 lg:grid-cols-2">
            <div data-reveal>
              <SectionLabel index="04" text="quickstart" />
              <h2 className="mt-5 font-mono text-3xl font-bold tracking-tight text-white sm:text-4xl">
                Running in <span className="text-[#56d9ff] text-glow-soft">minutes</span>, not days.
              </h2>
              <p className="mt-4 text-gray-400">
                One command starts backend, frontend and database. No cloud account, no paid
                dependency, no hidden configuration.
              </p>

              <div className="mt-8 grid grid-cols-3 gap-3 font-mono">
                {[
                  { port: '8080', label: 'Backend API', color: 'text-[#7cff6b]', border: 'hover:border-[#7cff6b]/50' },
                  { port: '3000', label: 'Frontend UI', color: 'text-[#56d9ff]', border: 'hover:border-[#56d9ff]/50' },
                  { port: '3306', label: 'MySQL', color: 'text-[#ffc466]', border: 'hover:border-[#ffc466]/50' },
                ].map((p) => (
                  <div
                    key={p.port}
                    className={`rounded-lg border border-white/10 bg-white/[0.02] px-4 py-4 text-center transition-colors ${p.border}`}
                  >
                    <div className={`text-2xl font-bold ${p.color}`}>{p.port}</div>
                    <div className="mt-1 text-[11px] uppercase tracking-widest text-gray-500">{p.label}</div>
                  </div>
                ))}
              </div>

              <div className="mt-8 rounded-lg border border-[#ffc466]/25 bg-[#ffc466]/5 px-4 py-3.5 font-mono text-xs leading-relaxed text-[#ffc466]/90">
                ! logs are untrusted input — rendered as data, never as markup
              </div>
            </div>

            <div data-reveal>
              <div className="panel overflow-hidden rounded-xl">
                <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
                  <span className="font-mono text-[11px] text-gray-500">~/projects/cloud-monitoring</span>
                  <span className="font-mono text-[11px] text-gray-600">bash — 96×24</span>
                </div>
                <pre className="overflow-x-auto px-5 py-5 font-mono text-[13px] leading-7 text-gray-300">
                  <code>
                    <span className="text-gray-600"># clone and enter the repo</span>{'\n'}
                    <span className="text-[#7cff6b]">$</span> git clone {'<repository-url>'}{'\n'}
                    <span className="text-[#7cff6b]">$</span> cd cloud-monitoring{'\n\n'}
                    <span className="text-gray-600"># start backend · frontend · mysql</span>{'\n'}
                    <span className="text-[#7cff6b]">$</span> docker compose up -d{'\n\n'}
                    <span className="text-gray-600"># verify health</span>{'\n'}
                    <span className="text-[#7cff6b]">$</span> curl localhost:8080/actuator/health{'\n'}
                    <span className="text-gray-500">{'{ "status": "UP" }'}</span>{'\n'}
                    <span className="text-[#7cff6b]">$</span> open http://localhost:3000{'\n'}
                    <span className="text-[#7cff6b]">$</span> <span className="caret" />
                  </code>
                </pre>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                {[
                  { k: 'migrations', v: 'flyway' },
                  { k: 'tests', v: 'junit 5' },
                  { k: 'health', v: 'actuator' },
                ].map((m) => (
                  <div
                    key={m.k}
                    className="rounded-lg border border-white/10 bg-white/[0.02] px-4 py-3 text-center font-mono"
                  >
                    <div className="text-[10px] uppercase tracking-widest text-gray-600">{m.k}</div>
                    <div className="mt-1 text-xs text-gray-300">{m.v}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ================= CTA ================= */}
      <section className="px-4 pb-24 sm:px-6 lg:px-8">
        <div data-reveal className="relative mx-auto max-w-6xl overflow-hidden rounded-2xl border border-[#7cff6b]/25 bg-gradient-to-br from-[#0d1210] via-[#0a0d12] to-[#0b0a12] px-6 py-16 text-center sm:px-12">
          <div className="pointer-events-none absolute inset-0 bg-dotgrid opacity-[0.07]" />
          <div className="pointer-events-none absolute -top-24 left-1/2 h-64 w-[560px] -translate-x-1/2 rounded-full bg-[#7cff6b]/15 blur-[90px]" />

          <div className="relative">
            <div className="font-mono text-xs uppercase tracking-[0.35em] text-[#7cff6b]">
              stage 1 — project foundation
            </div>
            <h2 className="mt-5 font-mono text-3xl font-bold tracking-tight text-white sm:text-4xl lg:text-5xl">
              Build observability that tells the truth.
            </h2>
            <p className="mx-auto mt-5 max-w-2xl text-gray-400">
              Reliability before feature count. Correctness before visual polish. Fork the repo, run
              it locally, extend it for your needs.
            </p>
            <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <a
                href="#getting-started"
                className="w-full rounded-md bg-[#7cff6b] px-8 py-3.5 font-mono text-sm font-semibold text-black transition-all hover:shadow-[0_0_40px_rgba(124,255,107,0.6)] sm:w-auto"
              >
                get_started →
              </a>
              <a
                href="#architecture"
                className="w-full rounded-md border border-white/20 px-8 py-3.5 font-mono text-sm text-gray-300 transition-colors hover:border-[#56d9ff]/60 hover:text-[#56d9ff] sm:w-auto"
              >
                read_the_architecture
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* ================= FOOTER ================= */}
      <footer className="border-t border-white/10 bg-[#040508]">
        <div className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
          <div className="grid gap-10 md:grid-cols-4">
            <div>
              <div className="flex items-center gap-2.5">
                <Logo size="sm" />
                <span className="font-mono text-sm font-semibold tracking-[0.2em] text-white">
                  CLOUD<span className="text-[#7cff6b]">MONITOR</span>
                </span>
              </div>
              <p className="mt-4 max-w-xs text-sm leading-relaxed text-gray-500">
                A production-quality observability platform for logs, metrics, dashboards and service
                health — tenant-safe by design.
              </p>
            </div>

            {[
              {
                title: 'product',
                links: [
                  { label: 'features', href: '#features' },
                  { label: 'architecture', href: '#architecture' },
                  { label: 'principles', href: '#principles' },
                  { label: 'quickstart', href: '#getting-started' },
                ],
              },
              {
                title: 'resources',
                links: [
                  { label: 'documentation', href: '#getting-started' },
                  { label: 'api reference', href: '#architecture' },
                  { label: 'security model', href: '#principles' },
                  { label: 'contributing', href: '#top' },
                ],
              },
              {
                title: 'legal',
                links: [
                  { label: 'privacy policy', href: '#top' },
                  { label: 'terms of service', href: '#top' },
                  { label: 'license', href: '#top' },
                ],
              },
            ].map((col) => (
              <div key={col.title}>
                <h4 className="font-mono text-xs uppercase tracking-[0.25em] text-gray-400">
                  {col.title}
                </h4>
                <ul className="mt-4 space-y-2.5 text-sm">
                  {col.links.map((l) => (
                    <li key={l.label}>
                      <a href={l.href} className="text-gray-500 transition-colors hover:text-[#7cff6b]">
                        {l.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-white/10 pt-6 font-mono text-xs text-gray-600 sm:flex-row">
            <span>© {new Date().getFullYear()} CloudMonitor — built for reliability, security and DX.</span>
            <span className="flex items-center gap-2">
              <span className="pulse-dot inline-block h-1.5 w-1.5 rounded-full bg-[#7cff6b]" />
              status: all systems nominal
            </span>
          </div>
        </div>
      </footer>
    </div>
  )
}

export default App
