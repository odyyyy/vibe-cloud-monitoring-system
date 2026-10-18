import React from 'react'
import type { ComponentType } from 'react'
import { SectionHeading } from '../components/SectionHeading'
import { InstallCodePanel } from '../components/InstallCodePanel'
import { useInView } from '../hooks/useInView'
import { IconLayers, IconGlobe, IconPulse, IconChart } from '../../shared/icons'

type LandingIconProps = { className?: string }
type LandingIcon = ComponentType<LandingIconProps>

type FlowAccent = {
  text: string
  iconBox: string
  iconGlow: string
  dot: string
  connector: string
  packet: string
}

type FlowStep = {
  n: string
  title: string
  body: string
  icon: LandingIcon
  meta: string
  accent: FlowAccent
}

const violetAccent: FlowAccent = {
  text: 'text-violet-300',
  iconBox: 'border-violet-400/25 bg-violet-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(139,92,246,0.55)]',
  dot: 'bg-violet-400',
  connector: 'from-violet-400/50',
  packet: 'bg-violet-300 shadow-[0_0_8px_2px_rgba(139,92,246,0.7)]',
}

const cyanAccent: FlowAccent = {
  text: 'text-cyan-300',
  iconBox: 'border-cyan-400/25 bg-cyan-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(34,211,238,0.55)]',
  dot: 'bg-cyan-400',
  connector: 'from-cyan-400/50',
  packet: 'bg-cyan-300 shadow-[0_0_8px_2px_rgba(34,211,238,0.7)]',
}

const limeAccent: FlowAccent = {
  text: 'text-lime-300',
  iconBox: 'border-lime-400/25 bg-lime-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(163,230,53,0.5)]',
  dot: 'bg-lime-400',
  connector: 'from-lime-400/50',
  packet: 'bg-lime-300 shadow-[0_0_8px_2px_rgba(163,230,53,0.7)]',
}

const amberAccent: FlowAccent = {
  text: 'text-amber-300',
  iconBox: 'border-amber-400/25 bg-amber-400/10',
  iconGlow: 'group-hover:shadow-[0_0_28px_-6px_rgba(251,191,36,0.5)]',
  dot: 'bg-amber-400',
  connector: 'from-amber-400/50',
  packet: 'bg-amber-300 shadow-[0_0_8px_2px_rgba(251,191,36,0.7)]',
}

const flowItems: FlowStep[] = [
  {
    n: '01',
    title: 'Enroll',
    body: 'Create a project and environment, then issue a scoped agent credential.',
    icon: IconLayers,
    meta: 'identity · scoped token',
    accent: violetAccent,
  },
  {
    n: '02',
    title: 'Deploy',
    body: 'Run the Go collector as a Docker container in your own infrastructure.',
    icon: IconGlobe,
    meta: 'docker · customer vpc',
    accent: cyanAccent,
  },
  {
    n: '03',
    title: 'Ingest',
    body: 'Metrics and logs stream over authenticated HTTPS with bounded batches.',
    icon: IconPulse,
    meta: 'https · bounded batches',
    accent: limeAccent,
  },
  {
    n: '04',
    title: 'Explore',
    body: 'Query, visualize and share dashboards from a single pane of glass.',
    icon: IconChart,
    meta: 'dashboards · single pane',
    accent: amberAccent,
  },
]

const installSteps = [
  'Create a project and environment in the console.',
  'Copy the scoped enrollment token — it only grants telemetry write access.',
  'Run the agent and watch metrics and logs appear in real time.',
]

const installChips = ['Go 1.22+', 'Docker', 'OpenTelemetry', 'HTTPS only']

function FlowConnector({ accent }: { accent: FlowAccent }) {
  return (
    <div
      className={`absolute -right-3 top-1/2 hidden h-px w-3 -translate-y-1/2 bg-gradient-to-r to-transparent lg:block ${accent.connector}`}
      aria-hidden="true"
    >
      <span className={`vp-packet absolute top-1/2 h-1 w-1 -translate-y-1/2 rounded-full ${accent.packet}`} />
    </div>
  )
}

function FlowCard({ step, index, inView }: { step: FlowStep; index: number; inView: boolean }) {
  const Icon = step.icon
  return (
    <div className="relative">
      <div
        className={`card-hover group h-full rounded-2xl border border-white/10 bg-panel/70 p-6 ${
          inView ? 'vp-step' : 'opacity-0'
        }`}
        style={{ animationDelay: `${index * 110}ms` }}
      >
        <div className="flex items-center justify-between">
          <div
            className={`flex h-10 w-10 items-center justify-center rounded-xl border transition-shadow duration-500 ${step.accent.iconBox} ${step.accent.iconGlow}`}
          >
            <Icon className={`h-5 w-5 ${step.accent.text} transition-transform duration-300 group-hover:scale-110`} />
          </div>
          <span className={`mono text-lg ${step.accent.text}`}>{step.n}</span>
        </div>

        <h3 className="font-display mt-5 text-lg font-semibold text-slate-100 transition-colors duration-300 group-hover:text-white">
          {step.title}
        </h3>
        <p className="mt-2 text-sm leading-relaxed text-slate-400">{step.body}</p>

        <div className="mono mt-5 flex items-center gap-2 border-t border-white/5 pt-4 text-[10px] uppercase tracking-wider text-slate-500">
          <span className={`h-1.5 w-1.5 rounded-full ${step.accent.dot}`} />
          <span>{step.meta}</span>
        </div>
      </div>
      {index < flowItems.length - 1 ? <FlowConnector accent={step.accent} /> : null}
    </div>
  )
}

export function HowItWorks() {
  const { ref: flowRef, inView: flowInView } = useInView<HTMLDivElement>()
  const { ref: installRef, inView: installInView } = useInView<HTMLDivElement>()

  return (
    <>
      <style>{`
        @keyframes vp-step-in {
          from { opacity: 0; transform: translateY(16px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .vp-step {
          opacity: 0;
          animation: vp-step-in 0.7s cubic-bezier(0.22, 1, 0.36, 1) forwards;
        }
        @keyframes vp-packet {
          0% { left: -2px; opacity: 0; }
          15% { opacity: 1; }
          85% { opacity: 1; }
          100% { left: calc(100% + 2px); opacity: 0; }
        }
        .vp-packet {
          animation: vp-packet 2.8s ease-in-out infinite;
        }
        @media (prefers-reduced-motion: reduce) {
          .vp-step { opacity: 1; animation: none; }
          .vp-packet { animation: none; opacity: 0.6; }
        }
      `}</style>

      <section id="flow" className="relative overflow-hidden border-y border-white/5 bg-canvas-2 py-24">
        <div className="bg-grid-fine absolute inset-0 opacity-60" />
        <div className="orb -left-24 top-12 h-64 w-64 bg-cyan-500/30" />
        <div className="orb -right-20 bottom-0 h-72 w-72 bg-violet-600/25" />
        <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-cyan-400/40 to-transparent" />

        <div className="relative mx-auto max-w-7xl px-6">
          <div className="flex flex-wrap items-end justify-between gap-6">
            <SectionHeading
              tag="Data flow"
              title={
                <>
                  From agent to dashboard in <span className="text-gradient">one flow.</span>
                </>
              }
              body="The control plane stays separate from the telemetry plane — but for your team, the path is a straight line."
            />
   
          </div>

          <div ref={flowRef} className="mt-14 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
            {flowItems.map((step, index) => (
              <FlowCard key={step.n} step={step} index={index} inView={flowInView} />
            ))}
          </div>
        </div>
      </section>

      <section id="install" className="relative overflow-hidden py-24">
        <div className="orb left-1/2 top-1/3 h-[420px] w-[620px] -translate-x-1/2 bg-violet-600/15" />
        <div className="orb -left-16 bottom-0 h-56 w-56 bg-cyan-500/15" />

        <div className="relative mx-auto max-w-7xl px-6">
          <div ref={installRef} className="grid items-center gap-12 lg:grid-cols-2">
            <div>
              <div className="flex flex-wrap items-end justify-between gap-4">
                <SectionHeading
                  tag="Quick start"
                  title={
                    <>
                      Observe your first service in <span className="text-gradient">five minutes.</span>
                    </>
                  }
                  body="One scoped credential, one container, and telemetry starts flowing. Credentials rotate without rebuilding the image."
                />
                <div className="mono pb-1.5 text-xs text-slate-500">
                  <span className="text-slate-600">{'//'}</span> setup ~ 5 min
                </div>
              </div>

              <ol className="relative mt-8 space-y-5">
                <div
                  className="absolute bottom-3 left-[13px] top-3 w-px bg-gradient-to-b from-cyan-400/40 via-white/10 to-transparent"
                  aria-hidden="true"
                />
                {installSteps.map((text, index) => (
                  <li key={text} className="group flex gap-4">
                    <span className="mono relative z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-cyan-400/40 bg-canvas-2 text-[11px] text-cyan-300 transition-colors duration-300 group-hover:border-cyan-300 group-hover:bg-cyan-400/10 group-hover:text-cyan-200">
                      {index + 1}
                    </span>
                    <span
                      className={`pt-0.5 text-sm leading-relaxed text-slate-300 transition-all duration-300 group-hover:text-slate-100 ${
                        installInView ? 'vp-step' : 'opacity-0'
                      }`}
                      style={{ animationDelay: `${index * 90}ms` }}
                    >
                      {text}
                    </span>
                  </li>
                ))}
              </ol>

              <div className="mt-8 flex flex-wrap gap-2">
                {installChips.map((chip) => (
                  <span key={chip} className="chip mono">
                    {chip}
                  </span>
                ))}
              </div>
            </div>

            <div className={installInView ? 'vp-step' : 'opacity-0'} style={{ animationDelay: '150ms' }}>
              <InstallCodePanel />
            </div>
          </div>
        </div>
      </section>
    </>
  )
}
